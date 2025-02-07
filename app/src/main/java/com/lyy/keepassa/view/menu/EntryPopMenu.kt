/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.menu

import android.annotation.SuppressLint
import android.text.Html
import android.text.Spanned
import android.view.Gravity
import android.view.MenuInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.arialyy.frame.router.Routerfit
import com.arialyy.frame.util.ReflectionUtil
import com.arialyy.frame.util.ResUtil
import com.keepassdroid.database.PwEntry
import com.keepassdroid.database.PwEntryV4
import com.lyy.keepassa.R
import com.lyy.keepassa.base.BaseApp
import com.lyy.keepassa.router.DialogRouter
import com.lyy.keepassa.util.ClipboardUtil
import com.lyy.keepassa.util.HitUtil
import com.lyy.keepassa.util.KdbUtil
import com.lyy.keepassa.util.KpaUtil
import com.lyy.keepassa.util.VibratorUtil
import com.lyy.keepassa.view.dialog.OnMsgBtClickListener
import com.lyy.keepassa.view.dir.ChooseGroupActivity

/**
 * 群组长按菜单
 * @param x 偏移量
 */
@SuppressLint("RestrictedApi")
class EntryPopMenu(
  private val context: FragmentActivity,
  view: View,
  private val entry: PwEntry,
  private val x: Int,
  private val isInRecycleBin: Boolean = false
) : IPopMenu {
  private val popup: PopupMenu = PopupMenu(context, view, Gravity.START)
  private val help: MenuPopupHelper

  init {
    val inflater: MenuInflater = popup.menuInflater
    inflater.inflate(R.menu.pop_entry_summary, popup.menu)

    popup.menu.findItem(R.id.undo).isVisible = isInRecycleBin
    var hasOtp = false
    if (BaseApp.isV4) {
      for (d in (entry as PwEntryV4).strings) {
        if (d.key.contains("TOTP Seed") || d.key.contains("otp") || d.key.contains("hotp")) {
          hasOtp = true
          break
        }
      }
    }
    popup.menu.findItem(R.id.copy_totp).isVisible = hasOtp

    // 以下代码为强制显示icon
    val mPopup = ReflectionUtil.getField(PopupMenu::class.java, "mPopup")
    mPopup.isAccessible = true
    help = mPopup.get(popup) as MenuPopupHelper
    help.setForceShowIcon(true)
    popup.setOnMenuItemClickListener { item ->
      when (item.itemId) {
        R.id.del -> {
          delEntry()
        }
        R.id.copy_user -> {
          val userName = KdbUtil.getUserName(entry)
          ClipboardUtil.get()
            .copyDataToClip(userName)
          HitUtil.toaskShort(context.getString(R.string.hint_copy_user))
        }
        R.id.copy_pw -> {
          val pass = KdbUtil.getPassword(entry)
          ClipboardUtil.get()
            .copyDataToClip(pass)
          HitUtil.toaskShort(context.getString(R.string.hint_copy_pass))
        }
        R.id.copy_totp -> {
          Routerfit.create(DialogRouter::class.java).showTotpDisplayDialog(entry.uuid.toString())
        }
        R.id.undo, R.id.move -> {
          ChooseGroupActivity.moveEntry(context, entry.uuid)
        }
//        R.id.multi_choice -> {
//          EventBus.getDefault().post(MultiChoiceEvent())
//        }
      }
      dismiss()
      true
    }
  }

  @SuppressLint("StringFormatMatches")
  private fun delEntry() {
    // 是否直接删除条目
    val deleteDirectly = PreferenceManager.getDefaultSharedPreferences(BaseApp.APP)
      .getBoolean(context.getString(R.string.set_key_delete_no_recycle_bin), false)

    if (deleteDirectly) {
      handleDelEntry()
      return
    }

    val msg: Spanned = if (BaseApp.isV4) {
      if (isInRecycleBin) {
        Html.fromHtml(context.getString(R.string.hint_del_entry_no_recycle, entry.title))
      } else {
        Html.fromHtml(context.getString(R.string.hint_del_entry, entry.title, entry.title))
      }
    } else {
      Html.fromHtml(context.getString(R.string.hint_del_entry_no_recycle, entry.title))
    }

    Routerfit.create(DialogRouter::class.java).showMsgDialog(
      msgTitle = ResUtil.getString(R.string.del_entry),
      msgContent = msg,
      btnClickListener = object : OnMsgBtClickListener {
        override fun onCover(v: Button) {
        }

        override fun onEnter(v: Button) {
          handleDelEntry()
        }

        override fun onCancel(v: Button) {
        }
      }
    )
  }

  /**
   * 处理删除条目
   */
  private fun handleDelEntry() {
    KpaUtil.kdbHandlerService.deleteEntry(entry as PwEntryV4) {
      HitUtil.toaskShort(
        "${context.getString(R.string.del_entry)}${context.getString(R.string.success)}"
      )
      VibratorUtil.vibrator(300)
    }
  }

  fun dismiss() {
    popup.dismiss()
  }

  public fun show() {
    help.show(x, 0)
  }

  fun getPopMenu(): PopupMenu {
    return popup
  }
}