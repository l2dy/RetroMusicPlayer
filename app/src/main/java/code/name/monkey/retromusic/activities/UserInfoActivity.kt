/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.activities

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.widget.Toast
import code.name.monkey.appthemehelper.util.ColorUtil
import code.name.monkey.appthemehelper.util.MaterialValueHelper
import code.name.monkey.retromusic.Constants.USER_BANNER
import code.name.monkey.retromusic.Constants.USER_PROFILE
import code.name.monkey.retromusic.activities.base.AbsBaseActivity
import code.name.monkey.retromusic.databinding.ActivityUserInfoBinding
import code.name.monkey.retromusic.extensions.accentColor
import code.name.monkey.retromusic.extensions.applyToolbar
import code.name.monkey.retromusic.glide.GlideApp
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.util.ImageUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.dhaval2404.imagepicker.ImagePicker
import com.github.dhaval2404.imagepicker.constant.ImageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UserInfoActivity : AbsBaseActivity() {

    private lateinit var binding: ActivityUserInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusbarColorAuto()
        setTaskDescriptionColorAuto()
        applyToolbar(binding.toolbar)

        binding.nameContainer.accentColor()
        binding.name.setText(PreferenceUtil.userName)

        binding.userImage.setOnClickListener {
            pickNewPhoto()
        }

        binding.bannerImage.setOnClickListener {
            selectBannerImage()
        }

        binding.next.setOnClickListener {
            val nameString = binding.name.text.toString().trim { it <= ' ' }
            if (TextUtils.isEmpty(nameString)) {
                Toast.makeText(this, "Umm you're name can't be empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PreferenceUtil.userName = nameString
            setResult(Activity.RESULT_OK)
            finish()
        }

        val textColor =
            MaterialValueHelper.getPrimaryTextColor(this, ColorUtil.isColorLight(accentColor()))
        binding.next.backgroundTintList = ColorStateList.valueOf(accentColor())
        binding.next.iconTint = ColorStateList.valueOf(textColor)
        binding.next.setTextColor(textColor)
        loadProfile()
    }

    private fun loadProfile() {
        binding.bannerImage.let {
            GlideApp.with(this)
                .asBitmap()
                .load(RetroGlideExtension.getBannerModel())
                .profileBannerOptions(RetroGlideExtension.getBannerModel())
                .into(it)
        }
        GlideApp.with(this).asBitmap()
            .load(RetroGlideExtension.getUserModel())
            .userProfileOptions(RetroGlideExtension.getUserModel())
            .into(binding.userImage)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectBannerImage() {
        ImagePicker.with(this)
            .compress(1440)
            .provider(ImageProvider.GALLERY)
            .crop(16f, 9f)
            .start(PICK_BANNER_REQUEST)
    }

    private fun pickNewPhoto() {
        ImagePicker.with(this)
            .provider(ImageProvider.GALLERY)
            .cropSquare()
            .compress(1440)
            .start(PICK_IMAGE_REQUEST)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == PICK_IMAGE_REQUEST) {
            val fileUri = data?.data
            fileUri?.let { setAndSaveUserImage(it) }
        } else if (resultCode == Activity.RESULT_OK && requestCode == PICK_BANNER_REQUEST) {
            val fileUri = data?.data
            fileUri?.let { setAndSaveBannerImage(it) }
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Task Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAndSaveBannerImage(fileUri: Uri) {
        Glide.with(this)
            .asBitmap()
            .load(fileUri)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(object : RequestListener<Bitmap> {
                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    resource?.let { saveImage(it, USER_BANNER) }
                    return false
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .into(binding.bannerImage)
    }

    private fun saveImage(bitmap: Bitmap, fileName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val appDir = applicationContext.filesDir
            val file = File(appDir, fileName)
            var successful = false
            try {
                val os = BufferedOutputStream(FileOutputStream(file))
                successful = ImageUtil.resizeBitmap(bitmap, 2048)
                    .compress(Bitmap.CompressFormat.WEBP, 100, os)
                withContext(Dispatchers.IO) { os.close() }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (successful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UserInfoActivity, "Updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setAndSaveUserImage(fileUri: Uri) {
        Glide.with(this)
            .asBitmap()
            .load(fileUri)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(object : RequestListener<Bitmap> {
                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    resource?.let { saveImage(it, USER_PROFILE) }
                    return false
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .into(binding.userImage)
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 9002
        private const val PICK_BANNER_REQUEST = 9004
    }
}
