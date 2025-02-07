package com.lyy.keepassa.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import timber.log.Timber

/**
 * @Author laoyuyu
 * @Description
 * @Date 10:42 上午 2022/1/27
 **/

/**
 * @return true 有效
 */
private fun checkoutContextEffective(context: Context): Boolean {
  if (context is Activity) {
    if (context.isFinishing || context.isDestroyed) {
      Timber.w("activity已经被销毁，不加载图片")
      return false
    }
  }
  return true
}

fun ImageView.loadImg(context: Context, @DrawableRes resId: Int) {
  if (checkoutContextEffective(context)) {
    Glide.with(context).load(resId).into(this)
  }
}

fun Button.loadBackground(context: Context, imgUrl: String?) {
  if (checkoutContextEffective(context)) {
    Glide.with(context).asDrawable().load(imgUrl).into(object : SimpleTarget<Drawable>() {
      override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
        this@loadBackground.background = resource
      }
    })
  }
}

fun ImageView.loadImg(context: Context, imgUrl: String?) {
  if (checkoutContextEffective(context)) {
    Glide.with(context).load(imgUrl).into(this)
  }
}

fun ImageView.loadImg(context: Context, imgBm: Bitmap?) {
  if (checkoutContextEffective(context) && imgBm != null && !imgBm.isRecycled) {
    Glide.with(context).load(imgBm).into(this)
  }
}

fun ImageView.loadImg(context: Context, byteArray: ByteArray?) {
  if (checkoutContextEffective(context) && byteArray != null && byteArray.isNotEmpty()) {
    Glide.with(context).load(byteArray).into(this)
  }
}

fun AppCompatImageView.loadImg(context: Context, @DrawableRes resId: Int) {
  if (checkoutContextEffective(context)) {
    Glide.with(context).load(resId).into(this)
  }
}

fun AppCompatImageView.loadImg(context: Context, imgUrl: String?) {
  if (checkoutContextEffective(context)) {
    Glide.with(context).load(imgUrl).into(this)
  }
}

fun AppCompatImageView.loadImg(context: Context, imgBm: Bitmap?) {
  if (checkoutContextEffective(context) && imgBm != null && !imgBm.isRecycled) {
    Glide.with(context).load(imgBm).into(this)
  }
}

fun AppCompatImageView.loadImg(context: Context, byteArray: ByteArray?) {
  if (checkoutContextEffective(context) && byteArray != null && byteArray.isNotEmpty()) {
    Glide.with(context).load(byteArray).into(this)
  }
}
