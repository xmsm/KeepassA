/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap.CompressFormat.PNG
import android.view.View
import com.arialyy.frame.config.CommonConstant
import com.keepassdroid.database.PwDatabaseV4
import com.keepassdroid.database.PwEntry
import com.keepassdroid.database.PwEntryV3
import com.keepassdroid.database.PwEntryV4
import com.keepassdroid.database.PwGroupV4
import com.keepassdroid.database.PwIconCustom
import com.keepassdroid.database.PwIconStandard
import com.keepassdroid.database.SearchParametersV4
import com.keepassdroid.database.security.ProtectedString
import com.lyy.keepassa.base.BaseApp
import com.lyy.keepassa.service.autofill.model.AutoFillFieldMetadataCollection
import com.lyy.keepassa.util.IconUtil
import com.lyy.keepassa.util.KdbUtil
import com.lyy.keepassa.util.KpaUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Singleton autofill data repository that stores autofill fields to SharedPreferences.
 * Disclaimer: you should not store sensitive fields like user data unencrypted. This is done
 * here only for simplicity and learning purposes.
 */
object KDBAutoFillRepository {

  /**
   * 通过包名获取填充数据
   */
  fun getAutoFillDataByPackageName(pkgName: String): MutableList<PwEntry>? {
    if (BaseApp.KDB?.pm == null){
      return null
    }
    Timber.d("getFillDataByPkgName, pkgName = $pkgName")
    val listStorage = ArrayList<PwEntry>()
    KdbUtil.searchEntriesByPackageName(pkgName, listStorage)
    if (listStorage.isEmpty()) {
      val sp = SearchParametersV4()
      val strs = pkgName.split(".")
      // 如果没有，则从url检索
      for (s in strs) {
        if (CommonConstant.domainSuffix.contains(s)){
          continue
        }
        sp.setupNone()
        sp.searchInUrls = true
        sp.searchString = s
        BaseApp.KDB!!.pm.rootGroup.searchEntries(sp, listStorage)
      }

      if (listStorage.isEmpty()) {
        return null
      }
    }

    return listStorage.toSet().toMutableList()
  }

  /**
   * 通过url获取填充数据
   */
  fun getAutoFillDataByDomain(domain: String): ArrayList<PwEntry>? {
    Timber.d("getFillDataByDomain, domain = $domain")
    val listStorage = ArrayList<PwEntry>()
    KdbUtil.searchEntriesByDomain(domain, listStorage)
    if (listStorage.isEmpty()) {
      return null
    }
    return listStorage
  }

  /**
   * 保存数据到数据库
   */
  fun saveDataToKdb(
    context: Context,
    apkPkgName: String,
    autofillFields: AutoFillFieldMetadataCollection
  ) {
    if (BaseApp.KDB?.pm == null){
      Timber.e("数据库为空")
      return
    }
    val listStorage = ArrayList<PwEntry>()
    KdbUtil.searchEntriesByPackageName(apkPkgName, listStorage)
    val entry: PwEntry
    if (listStorage.isEmpty()) {
      if (BaseApp.isV4) {
        entry = PwEntryV4(BaseApp.KDB!!.pm.rootGroup as PwGroupV4)
        val icon = IconUtil.getAppIcon(context, apkPkgName)
        if (icon != null) {
          val baos = ByteArrayOutputStream()
          icon.compress(PNG, 100, baos)
          val datas: ByteArray = baos.toByteArray()
          val customIcon = PwIconCustom(UUID.randomUUID(), datas)
          entry.customIcon = customIcon
          (BaseApp.KDB!!.pm as PwDatabaseV4).putCustomIcons(customIcon)
          entry.strings["KP2A_URL_1"] = ProtectedString(false, "androidapp://$apkPkgName")
        }
      } else {
        entry = PwEntryV3()
        entry.setUrl("androidapp://$apkPkgName", BaseApp.KDB!!.pm)
      }
      val appName = getAppName(context, apkPkgName)
      entry.setTitle(appName ?: "newEntry", BaseApp.KDB!!.pm)
      entry.icon = PwIconStandard(0)
      KpaUtil.kdbHandlerService.addEntry(entry as PwEntryV4)
    } else {
      entry = listStorage[0]
      Timber.w("已存在含有【$apkPkgName】的条目，将更新条目")
    }

    for (hint in autofillFields.allAutoFillHints) {
      val fillFields = autofillFields.getFieldsForHint(hint) ?: continue
      for (fillField in fillFields) {
        fillField.autoFillField.textValue ?: continue
        if (fillField.autoFillType == View.AUTOFILL_TYPE_TEXT) {
          if (fillField.isPassword) {
            entry.setPassword(fillField.autoFillField.textValue, BaseApp.KDB!!.pm)
//            Log.d(TAG, "pass = ${fillField.textValue}")
          } else {
            entry.setUsername(fillField.autoFillField.textValue, BaseApp.KDB!!.pm)
//            Log.d(TAG, "userName = ${fillField.textValue}")
          }
        }
      }
    }
    KpaUtil.kdbHandlerService.saveDbByBackground()
    Timber.d("密码信息保存成功")
  }

  /**
   * 获取用户名和密码
   * @return first 用户名
   */
  fun getUserInfo(autofillFields: AutoFillFieldMetadataCollection): Pair<String?, String?> {
    var user: String? = null
    var pass: String? = null
    for (hint in autofillFields.allAutoFillHints) {
      val fillFields = autofillFields.getFieldsForHint(hint) ?: continue
      for (fillField in fillFields) {
        fillField.autoFillField.textValue ?: continue
        if (fillField.autoFillType == View.AUTOFILL_TYPE_TEXT) {
          if (fillField.isPassword && pass == null) {
            pass = fillField.autoFillField.textValue
          }
          if (!fillField.isPassword && user == null) {
            user = fillField.autoFillField.textValue
          }
        }
      }
    }
    return Pair(user, pass)
  }

  /**
   * 获取应用程序名称
   */
  fun getAppName(
    context: Context,
    apkPkgName: String
  ): String? {
    try {
      val packageManager = context.packageManager
      return packageManager.getApplicationLabel(
          packageManager.getApplicationInfo(
              apkPkgName,
              PackageManager.GET_META_DATA
          )
      )
          .toString()
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return null
  }

}