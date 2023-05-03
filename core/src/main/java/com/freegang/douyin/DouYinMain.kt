package com.freegang.douyin

import android.app.Application
import com.freegang.base.BaseHook
import com.freegang.config.Config
import com.freegang.xpler.core.EmptyHook
import com.freegang.xpler.core.hookClass
import com.freegang.xpler.utils.app.KAppCrashUtils
import com.freegang.xpler.utils.io.hasOperationStorage
import com.freegang.xpler.utils.log.KLogCat
import com.ss.android.ugc.aweme.app.host.AwemeHostApplication
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method

class DouYinMain(lpparam: XC_LoadPackage.LoadPackageParam) : BaseHook<EmptyHook>(lpparam) {
    companion object {
        var commonPageClazz: Class<*>? = null
        var emojiMethods: List<Method> = emptyList()
    }

    override fun onInit() {
        lpparam.hookClass(AwemeHostApplication::class.java)
            .method("onCreate") {
                onBefore {
                    val app = thisObject as Application

                    //日志工具
                    KLogCat.init(app)
                    KLogCat.openStorage()

                    //全局异常捕获工具
                    KAppCrashUtils.instance.init(app, "抖音异常崩溃!")

                    //文件读写权限检查
                    if (!app.hasOperationStorage) {
                        showToast(app, "抖音没有文件读写权限!")
                        return@onBefore
                    }

                    //加载配置
                    Config.read(app)

                    //初始化DexKit
                    initDexKit()

                    //Hook
                    HMainActivity(lpparam)
                    HMainFragment(lpparam)
                    HDetailActivity(lpparam)
                    HCommonPageFragment(lpparam)
                    HLazyFragmentPagerAdapter(lpparam)
                    HGifEmojiDetailActivity(lpparam)
                    HEmojiDetailDialog(lpparam)
                    HEmojiDetailDialogNew(lpparam)
                }
            }
    }

    private fun initDexKit() {
        System.loadLibrary("dexkit")
        DexKitBridge.create(lpparam.appInfo.sourceDir)?.use { bridge ->
            if (commonPageClazz == null) {
                val findMaps = bridge.batchFindClassesUsingStrings {
                    addQuery("CommonPage", setOf("a1128.b7947", "DetailActOtherNitaView"))
                }
                commonPageClazz = findMaps["CommonPage"]?.firstOrNull()?.getClassInstance(lpparam.classLoader)
            }

            if (emojiMethods.isEmpty()) {
                emojiMethods = bridge.findMethod {
                    methodReturnType = "V"
                    methodParamTypes = arrayOf("Lcom/ss/android/ugc/aweme/emoji/model/Emoji;")
                }.filter { it.isMethod }.map { it.getMethodInstance(lpparam.classLoader) }
            }
        }
    }
}