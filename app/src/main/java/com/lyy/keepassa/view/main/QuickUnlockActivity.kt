/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.main

import KDBAutoFillRepository
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.res.AssetManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.arialyy.frame.router.Routerfit
import com.arialyy.frame.util.KeyStoreUtil
import com.lyy.keepassa.R
import com.lyy.keepassa.base.BaseActivity
import com.lyy.keepassa.base.BaseApp
import com.lyy.keepassa.databinding.DialogQuickUnlockBinding
import com.lyy.keepassa.entity.QuickUnLockRecord
import com.lyy.keepassa.router.ActivityRouter
import com.lyy.keepassa.util.HitUtil
import com.lyy.keepassa.util.KeepassAUtil
import com.lyy.keepassa.util.NotificationUtil
import com.lyy.keepassa.util.QuickUnLockUtil
import com.lyy.keepassa.view.fingerprint.FingerprintModule
import com.lyy.keepassa.view.launcher.LauncherActivity
import com.lyy.keepassa.view.search.AutoFillEntrySearchActivity
import com.lyy.keepassa.widget.ShortPasswordView
import timber.log.Timber
import java.io.IOException
import java.util.UUID

/**
 * 快速解锁对话框
 */
@Route(path = "/launcher/quickLock")
class QuickUnlockActivity : BaseActivity<DialogQuickUnlockBinding>() {
  private var isAutoFill = false
  private var apkPkgName = ""
  private lateinit var module: FingerprintModule
  private var fingerRecord: QuickUnLockRecord? = null
  private lateinit var keyStoreUtil: KeyStoreUtil

  /**
   * 搜索启动器
   */
  private val searchLauncher =
    registerForActivityResult(object : ActivityResultContract<String, Pair<Boolean, UUID?>?>() {
      override fun createIntent(context: Context, input: String?): Intent {
        val intent =
          Intent(this@QuickUnlockActivity, AutoFillEntrySearchActivity::class.java).apply {
            putExtra(LauncherActivity.KEY_PKG_NAME, input)
          }
        return intent
      }

      override fun parseResult(resultCode: Int, data: Intent?): Pair<Boolean, UUID?>? {
        if (data == null) {
          return null
        }
        val isSaveRelevance = data.getBooleanExtra(
          AutoFillEntrySearchActivity.EXTRA_IS_SAVE_RELEVANCE, false
        )
        val uid =
          data.getSerializableExtra(AutoFillEntrySearchActivity.EXTRA_ENTRY_ID) as UUID?
        return Pair(isSaveRelevance, uid)
      }
    }) { it ->
      // 搜索返回的数据
      if (it == null) {
        setResult(
          Activity.RESULT_OK,
          KeepassAUtil.instance.getFillResponse(this, intent, apkPkgName)
        )
        super.finish()
        return@registerForActivityResult
      }

      val isSaveRelevance = it.first

      if (isSaveRelevance) {
        setResult(
          Activity.RESULT_OK,
          KeepassAUtil.instance.getFillResponse(this, intent, apkPkgName)
        )
      } else {
        val id = it.second
        setResult(
          Activity.RESULT_OK,
          BaseApp.KDB.pm.entries[id]?.let {
            KeepassAUtil.instance.getFillResponse(this, intent, it, apkPkgName)
          }
        )
      }
      super.finish()
    }

  override fun initData(savedInstanceState: Bundle?) {
    module = ViewModelProvider(this).get(FingerprintModule::class.java)
    NotificationUtil.startQuickUnlockNotify(this)
    BaseApp.isLocked = true
    initUi()
    isAutoFill = intent.getBooleanExtra(KEY_IS_AUTH_FORM_FILL, false)
    if (isAutoFill) {
      apkPkgName = intent.getStringExtra(KEY_PKG_NAME) ?: ""
      if (apkPkgName.isBlank()) {
        BaseApp.KDB = null
        finish()
        return
      }
    }
  }

  private fun initUi() {
    BaseApp.dbRecord?.localDbUri?.let {
      module.getQuickUnlockRecord(it).observe(this, Observer { record ->
        if (record != null) {
          fingerRecord = record
          binding.fingerprint.visibility = View.VISIBLE
          binding.fingerprint.playAnimation()
          keyStoreUtil = KeyStoreUtil()
          binding.fingerprint.setOnClickListener {
            showBiometricPrompt()
          }
          return@Observer
        }
        binding.fingerprint.visibility = View.GONE
      })
    }


    binding.pass.setInputCompleteListener(object : ShortPasswordView.InputCompleteListener {
      override fun inputComplete(text: String) {
        if (QuickUnLockUtil.encryptStr(text) == BaseApp.shortPass) {
          BaseApp.isLocked = false
          turnActivity()
        } else {
          HitUtil.toaskShort(getString(R.string.error_pass))
        }
      }

      override fun invalidContent() {
      }
    })

    val sh = PreferenceManager.getDefaultSharedPreferences(BaseApp.APP)
    val passLen = sh
      .getString(getString(R.string.set_quick_pass_len), "3")!!
      .toString()
      .toInt()

    val value = sh.getString(getString(R.string.set_quick_pass_type), "1")!!
      .toString()
      .toInt()
    binding.title.text =
      resources.getStringArray(R.array.quick_pass_type_entries)[value - 1].format(passLen)

    binding.pass.setPassLen(passLen)

    binding.changeDb.setOnClickListener {
      KeepassAUtil.instance.turnLauncher(LauncherActivity.OPEN_TYPE_OPEN_DB)
    }

    startBgAnim()
  }

  /**
   * 显示验证指纹对话框
   */
  @SuppressLint("RestrictedApi")
  private fun showBiometricPrompt() {
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(getString(R.string.fingerprint_unlock))
      .setSubtitle(getString(R.string.verify_finger))
      .setNegativeButtonText(getString(R.string.cancel))
//        .setConfirmationRequired(false)
      .build()

    if (fingerRecord == null) {
      Timber.e("解锁记录为空")
      return
    }
    val biometricPrompt = BiometricPrompt(this, ArchTaskExecutor.getMainThreadExecutor(),
      object : AuthenticationCallback() {
        override fun onAuthenticationError(
          errorCode: Int,
          errString: CharSequence
        ) {
          val str = if (errorCode == ERROR_NEGATIVE_BUTTON) {
            "${getString(R.string.verify_finger)}${getString(R.string.cancel)}"
          } else {
            getString(R.string.verify_finger_fail)
          }
          HitUtil.snackShort(mRootView, str)
        }

        override fun onAuthenticationSucceeded(result: AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          turnActivity()
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          HitUtil.snackShort(mRootView, getString(R.string.verify_finger_fail))
        }
      })
    try {
      biometricPrompt.authenticate(
        promptInfo,
        CryptoObject(keyStoreUtil.getDecryptCipher(fingerRecord!!.passIv!!))
      )
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun startBgAnim() {
    try {
      binding.anim.setAnimation(
        assets.open("lockedAnim.json", AssetManager.ACCESS_STREAMING),
        "LottieCacheLock"
      )
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  override fun onResume() {
    super.onResume()
    if (!binding.anim.isAnimating) {
      binding.anim.resumeAnimation()
    }
  }

  override fun onPause() {
    super.onPause()
    if (binding.anim.isAnimating) {
      binding.anim.pauseAnimation()
    }
  }

  /**
   * 解锁成功进入特定页面
   * 如果MainActivity已启动，直接Finish当前
   * 如果MainActivity没有启动，启动MainActivity
   * 如果是自动填充进入，
   */
  private fun turnActivity() {
    BaseApp.isLocked = false
    NotificationUtil.startDbOpenNotify(this@QuickUnlockActivity)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && BaseApp.KDB != null && isAutoFill) {
      val datas = KDBAutoFillRepository.getAutoFillDataByPackageName(apkPkgName)
      // 如果查找不到数据，跳转到搜索页面
      if (datas == null || datas.isEmpty()) {
//      if (true) {
        searchLauncher.launch(packageName, ActivityOptionsCompat.makeSceneTransitionAnimation(this))
        return
      }
      val data = KeepassAUtil.instance.getFillResponse(this, intent, apkPkgName)
      setResult(Activity.RESULT_OK, data)
      finish()
    } else {
      Routerfit.create(ActivityRouter::class.java, this).toMainActivity(
        opt = ActivityOptionsCompat.makeSceneTransitionAnimation(this)
      )
    }
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    binding.pass.clean()
  }

  override fun onBackPressed() {
//    super.onBackPressed()
    moveTaskToBack(true)
  }

  override fun setLayoutId(): Int {
    return R.layout.dialog_quick_unlock
  }

  companion object {
    const val KEY_IS_AUTH_FORM_FILL = "KEY_IS_AUTH_FORM_FILL"
    const val KEY_PKG_NAME = "KEY_PKG_NAME"
    const val REQUEST_SEARCH_ENTRY_CODE = 0xa2

    /**
     * 从通知进入快速解锁页
     */
    internal fun createQuickUnlockPending(context: Context): PendingIntent {
      if (BaseApp.dbRecord == null) {
        return LauncherActivity.createLauncherPending(context)
      }

      return Intent(context, QuickUnlockActivity::class.java).let { notificationIntent ->
        PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
      }
    }

    internal fun startQuickUnlockActivity(
      context: Context,
      flags: Int = -1
    ) {
      if (BaseApp.dbRecord == null) {
        LauncherActivity.startLauncherActivity(context, flags)
        return
      }

      context.startActivity(Intent(context, QuickUnlockActivity::class.java).apply {
        if (flags != -1) {
          this.flags = flags
        }
      })
    }

    /**
     * 从自动填充快速解锁界面
     */
    internal fun getQuickUnlockSenderForResponse(
      context: Context,
      pkgName: String
    ): IntentSender {
      if (BaseApp.dbRecord == null) {
        return LauncherActivity.getAuthDbIntentSender(context, apkPackageName = pkgName)
      }
      val intent = Intent(context, QuickUnlockActivity::class.java).also {
        it.putExtra(KEY_IS_AUTH_FORM_FILL, true)
        it.putExtra(KEY_PKG_NAME, pkgName)
      }
      return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
        .intentSender
    }
  }
}