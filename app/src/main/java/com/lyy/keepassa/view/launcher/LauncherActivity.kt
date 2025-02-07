/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.launcher

import KDBAutoFillRepository
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.arialyy.frame.router.Routerfit
import com.lyy.keepassa.R
import com.lyy.keepassa.R.layout
import com.lyy.keepassa.base.BaseActivity
import com.lyy.keepassa.base.BaseApp
import com.lyy.keepassa.databinding.ActivityLauncherBinding
import com.lyy.keepassa.entity.DbHistoryRecord
import com.lyy.keepassa.event.ChangeDbEvent
import com.lyy.keepassa.event.DbHistoryEvent
import com.lyy.keepassa.router.ActivityRouter
import com.lyy.keepassa.router.FragmentRouter
import com.lyy.keepassa.util.EventBusHelper
import com.lyy.keepassa.util.KeepassAUtil
import com.lyy.keepassa.view.create.CreateEntryActivity
import com.lyy.keepassa.view.search.AutoFillEntrySearchActivity
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN

@Route(path = "/launcher/activity")
class LauncherActivity : BaseActivity<ActivityLauncherBinding>() {
  private val REQUEST_PERMISSION_CODE = 0xa1
  private val REQUEST_SEARCH_ENTRY_CODE = 0xa2
  private val REQUEST_SAVE_ENTRY_CODE = 0xa3
  private lateinit var module: LauncherModule
  private var changeDbFragment: ChangeDbFragment? = null
  private var openDbFragment: OpenDbFragment? = null
  private var isChangeDb = false

  /**
   * 是否由自动填充服务启动
   */
  private var isFromFill: Boolean = false

  /**
   * 是否由自动填充服务的保存启动
   */
  private var isFromFillSave: Boolean = false

  /**
   * 第三方应用包名
   */
  private var apkPkgName: String? = null

  /**
   * 启动类型，只有含有历史打开记录时，该记录才有效
   */
  @Autowired(name = KEY_OPEN_TYPE)
  @JvmField
  var type = OPEN_TYPE_OPEN_DB

  override fun setLayoutId(): Int {
    return layout.activity_launcher
  }

  override fun initData(savedInstanceState: Bundle?) {
    super.initData(savedInstanceState)
    ARouter.getInstance().inject(this)
    EventBusHelper.reg(this)

    // type = intent.getIntExtra(KEY_OPEN_TYPE, OPEN_TYPE_OPEN_DB)
    isFromFill = intent.getBooleanExtra(KEY_IS_AUTH_FORM_FILL, false)
    isFromFillSave = intent.getBooleanExtra(KEY_IS_AUTH_FORM_FILL_SAVE, false)
    apkPkgName = intent.getStringExtra(KEY_PKG_NAME)
    module = ViewModelProvider(this).get(LauncherModule::class.java)
    initUI()
    module.securityCheck(this)
  }

  override fun useAnim(): Boolean {
    return false
  }

  /**
   * 初始化界面
   */
  private fun initUI() {
    module.getLastOpenDbHistory(this)
      .observe(this, Observer { t ->
        val fragment: Fragment
        val tag: String
        if (t == null) {
          val p = buildChangDbFragment()
          tag = p.first
          changeDbFragment = p.second
          fragment = changeDbFragment!!
        } else {
          if (type == OPEN_TYPE_CHANGE_DB) {
            val p = buildChangDbFragment()
            tag = p.first
            changeDbFragment = p.second
            fragment = changeDbFragment!!
          } else {
            val p = buildOpenDbFragment(record = t)
            tag = p.first
            openDbFragment = p.second
            fragment = openDbFragment!!
          }
        }

        supportFragmentManager.beginTransaction()
          .replace(R.id.content, fragment, tag)
          .commitNow()
      })
  }

  override fun onResume() {
    super.onResume()
    // 如果数据库已经打开，直接启用到主页，用于快捷方式添加数据后返回的情况
    if (BaseApp.KDB != null && !BaseApp.isLocked) {
      Routerfit.create(ActivityRouter::class.java, this).toMainActivity(
        opt = ActivityOptionsCompat.makeSceneTransitionAnimation(this)
      )
    }
  }

  override fun finish() {
    // 如果是由自动填充服务启动，并且打开数据库成功，则构建相应的填充数据
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && BaseApp.KDB != null) {
      /*
       * 打开搜索界面
       */
      if (isFromFill) {
        val datas = KDBAutoFillRepository.getAutoFillDataByPackageName(apkPkgName!!)
        // 如果查找不到数据，跳转到搜索页面
        if (datas == null || datas.isEmpty()) {
          AutoFillEntrySearchActivity.turnSearchActivity(
            this, REQUEST_SEARCH_ENTRY_CODE,
            apkPkgName!!
          )
          return
        }

        // 将数据回调给service
        val data = KeepassAUtil.instance.getFillResponse(this, intent, apkPkgName!!)
        setResult(Activity.RESULT_OK, data)
      }

      /*
       * 打开创建条目界面
       */
      if (isFromFillSave) {
        startActivityForResult(
          Intent(this, CreateEntryActivity::class.java).apply {
            putExtra(KEY_IS_AUTH_FORM_FILL_SAVE, true)
            putExtra(KEY_PKG_NAME, intent.getStringExtra(KEY_PKG_NAME))
            putExtra(KEY_SAVE_USER_NAME, intent.getStringExtra(KEY_SAVE_USER_NAME))
            putExtra(KEY_SAVE_PASS, intent.getStringExtra(KEY_SAVE_PASS))
          }, REQUEST_SAVE_ENTRY_CODE, ActivityOptions.makeSceneTransitionAnimation(this)
            .toBundle()
        )
      }
    }
    super.finish()
  }

  /**
   * 切换数据库
   */
  fun changeDb() {
    if (changeDbFragment == null) {
      changeDbFragment = ChangeDbFragment()
    }
    supportFragmentManager.beginTransaction()
      .replace(R.id.content, changeDbFragment!!)
      .commitNow()
    isChangeDb = true
  }

  override fun onBackPressed() {
//    super.onBackPressed()
    if (isChangeDb) {
      supportFragmentManager.beginTransaction()
        .replace(R.id.content, openDbFragment!!)
        .commitNow()
      isChangeDb = false
    } else {
      super.onBackPressed()
    }
  }

  /**
   * da history is empty
   */
  @Subscribe(threadMode = MAIN)
  fun onHistoryEmpty(event: DbHistoryEvent) {
    if (event.isEmpty) {
      isChangeDb = false
    }
  }

  /**
   * 接收切换数据库的回调
   */
  @Subscribe(threadMode = MAIN)
  fun onDbChange(event: ChangeDbEvent) {
    val record = DbHistoryRecord(
      time = System.currentTimeMillis(),
      type = event.uriType.name,
      localDbUri = event.localFileUri.toString(),
      cloudDiskPath = event.cloudPath,
      keyUri = event.keyUri.toString(),
      dbName = event.dbName
    )
    var tag: String = OpenDbFragment.FM_TAG
    if (openDbFragment == null) {
      val p = buildOpenDbFragment(record)
      tag = p.first
      openDbFragment = p.second
    }
    supportFragmentManager.beginTransaction()
      .replace(R.id.content, openDbFragment!!, tag)
      .commitNowAllowingStateLoss()
    openDbFragment!!.updateData(dbRecord = record)
  }

  private fun buildChangDbFragment(): Pair<String, ChangeDbFragment> {
    var fragment = supportFragmentManager.findFragmentByTag(ChangeDbFragment.FM_TAG)
    if (fragment == null || fragment !is ChangeDbFragment) {
      fragment = Routerfit.create(FragmentRouter::class.java).getChangeDbFragment()
    }
    return Pair(ChangeDbFragment.FM_TAG, fragment)
  }

  private fun buildOpenDbFragment(record: DbHistoryRecord): Pair<String, OpenDbFragment> {
    var fragment = supportFragmentManager.findFragmentByTag(OpenDbFragment.FM_TAG)
    if (fragment == null || fragment !is OpenDbFragment) {
      fragment = Routerfit.create(FragmentRouter::class.java).getOpenDbFragment(isFromFill, record)
    }
    return Pair(OpenDbFragment.FM_TAG, fragment)
  }

  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    super.onActivityResult(requestCode, resultCode, data)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
      && resultCode == Activity.RESULT_OK
      && requestCode == REQUEST_SEARCH_ENTRY_CODE
    ) {
      // 搜索页返回的数据
      if (data != null) {
        val isSaveRelevance = data.getBooleanExtra(
          AutoFillEntrySearchActivity.EXTRA_IS_SAVE_RELEVANCE, false
        )

        if (isSaveRelevance) {
          setResult(
            Activity.RESULT_OK, KeepassAUtil.instance.getFillResponse(this, intent, apkPkgName!!)
          )
        } else {
          val id = data.getSerializableExtra(AutoFillEntrySearchActivity.EXTRA_ENTRY_ID)
          setResult(
            Activity.RESULT_OK,
            BaseApp.KDB.pm.entries[id]?.let {
              KeepassAUtil.instance.getFillResponse(
                this,
                intent,
                it,
                apkPkgName!!
              )
            }
          )
        }
      } else {
        setResult(
          Activity.RESULT_OK, KeepassAUtil.instance.getFillResponse(this, intent, apkPkgName!!)
        )
      }
      super.finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    EventBusHelper.unReg(this)
  }

  companion object {

    // Unique autofillId for dataset intents.
    const val KEY_IS_AUTH_FORM_FILL = "KEY_IS_AUTH_FORM_FILL"
    const val KEY_IS_AUTH_FORM_FILL_SAVE = "KEY_IS_AUTH_FORM_FILL_SAVE"
    const val KEY_PKG_NAME = "DATA_PKG_NAME"
    const val KEY_OPEN_TYPE = "KEY_OPEN_TYPE"
    const val OPEN_TYPE_CHANGE_DB = 1
    const val OPEN_TYPE_OPEN_DB = 2
    const val KEY_SAVE_USER_NAME = "KEY_SAVE_USER_NAME"
    const val KEY_SAVE_PASS = "KEY_SAVE_PASS"

    internal fun startLauncherActivity(
      context: Context,
      flags: Int = -1
    ) {
      context.startActivity(Intent(context, LauncherActivity::class.java).apply {
        if (flags != -1) {
          this.flags = flags
        }
      })
    }

    /**
     * 从通知进入登录页
     */
    internal fun createLauncherPending(context: Context): PendingIntent {
      return Intent(context, LauncherActivity::class.java).let { notificationIntent ->
        PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
      }
    }

    /**
     * 数据库未解锁
     * @param apkPackageName 第三方apk包名
     */
    internal fun getAuthDbIntentSender(
      context: Context,
      apkPackageName: String
    ): IntentSender {
      val intent = Intent(context, LauncherActivity::class.java).also {
        it.putExtra(KEY_IS_AUTH_FORM_FILL, true)
        it.putExtra(KEY_PKG_NAME, apkPackageName)
      }
      return PendingIntent.getActivity(
        context,
        1,
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
        .intentSender
    }

    /**
     * 数据库未解锁，保存数据时打开数据库，并保存
     */
    internal fun getAuthDbIntentSenderBySave(
      context: Context,
      apkPackageName: String,
      userName: String,
      pass: String
    ): IntentSender {
      val intent = Intent(context, LauncherActivity::class.java).also {
        it.putExtra(KEY_IS_AUTH_FORM_FILL_SAVE, true)
        it.putExtra(KEY_PKG_NAME, apkPackageName)
        it.putExtra(KEY_SAVE_USER_NAME, userName)
        it.putExtra(KEY_SAVE_PASS, pass)
      }
      return PendingIntent.getActivity(
        context,
        1,
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
        .intentSender
    }
  }
}