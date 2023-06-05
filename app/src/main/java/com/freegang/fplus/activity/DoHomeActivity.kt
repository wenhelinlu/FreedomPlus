package com.freegang.fplus.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.freegang.fplus.FreedomTheme
import com.freegang.fplus.R
import com.freegang.fplus.Themes
import com.freegang.fplus.resource.StringRes
import com.freegang.fplus.viewmodel.HomeVM
import com.freegang.ktutils.app.KAppUtils
import com.freegang.ktutils.app.appVersionName
import com.freegang.ui.component.FCard
import com.freegang.ui.component.FMessageDialog
import com.freegang.xpler.HookStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class DoHomeActivity : ComponentActivity() {
    private val model by viewModels<HomeVM>()

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun TopBarView() {

        var rotate by remember { mutableStateOf(0f) }
        val rotateAnimate by animateFloatAsState(
            targetValue = rotate,
            animationSpec = tween(durationMillis = Random.nextInt(500, 1500)),
        )

        //更新日志弹窗
        var showUpdateLogDialog by remember { mutableStateOf(false) }
        var updateLog by remember { mutableStateOf("") }
        if (showUpdateLogDialog) {
            FMessageDialog(
                title = "更新日志",
                onlyConfirm = true,
                confirm = "确定",
                onConfirm = { showUpdateLogDialog = false },
                content = {
                    LazyColumn(
                        modifier = Modifier,
                        content = {
                            item {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        TextView(context).apply {
                                            text = updateLog
                                            textSize = Themes.nowTypography.body1.fontSize.value
                                            setTextIsSelectable(true)
                                        }
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }


        //view
        TopAppBar(
            modifier = Modifier.padding(vertical = 24.dp),
            elevation = 0.dp,
            backgroundColor = Themes.nowColors.colors.background,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = StringRes.moduleTitle,
                        style = Themes.nowTypography.subtitle1,
                    )
                    Spacer(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        text = StringRes.moduleSubtitle,
                        style = Themes.nowTypography.subtitle2,
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_motion),
                    contentDescription = "检查更新/日志",
                    tint = Themes.nowColors.icon,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotateAnimate)
                        .combinedClickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                rotate = if (rotate == 0f) 360f else 0f
                            },
                            onLongClick = {
                                lifecycleScope.launch {
                                    updateLog = withContext(Dispatchers.IO) {
                                        val inputStream = assets.open("update.log")
                                        val bytes = inputStream.readBytes()
                                        val text = bytes.decodeToString()
                                        inputStream.close()
                                        text
                                    }
                                    showUpdateLogDialog = updateLog.isNotBlank()
                                }
                            }
                        )
                )
            }
        }
    }

    @Composable
    fun BodyView() {
        //旧数据迁移
        var showNeedMigrateOldDataDialog by remember { mutableStateOf(model.freedomData.exists()) }
        if (showNeedMigrateOldDataDialog) {
            var showMigrateToContent by remember { mutableStateOf("存在[Freedom]下载数据, 正在迁移至[Freedom+]下载目录!") }
            var showMigrateToConfirm by remember { mutableStateOf("请稍后...") }

            FMessageDialog(
                title = "提示",
                onlyConfirm = true,
                confirm = showMigrateToConfirm,
                onConfirm = {
                    if (showMigrateToConfirm == "确定") {
                        showNeedMigrateOldDataDialog = false
                    }
                },
                content = {
                    Text(text = showMigrateToContent)
                },
            )

            //数据迁移
            LaunchedEffect(key1 = "migrateData") {
                val result = withContext(Dispatchers.IO) {
                    model.freedomData.copyRecursively(
                        target = model.freedomPlusData,
                        overwrite = true,
                        onError = { _, _ ->
                            OnErrorAction.TERMINATE
                        },
                    )
                }
                showMigrateToConfirm = "确定"
                showMigrateToContent = if (!result) {
                    "旧数据迁移失败, 请手动将[外置存储器/Download/Freedom]目录合并至[外置存储器/DCIM/Freedom]中!"
                } else {
                    val deleteAll = model.freedomData.deleteRecursively()
                    if (deleteAll) "旧数据迁移成功!" else "旧数据迁移成功, 但旧数据删除失败, 请手动将[外置存储器/Download/Freedom]删除!"
                }
            }
        }

        //版本更新弹窗
        var showNewVersionDialog by remember { mutableStateOf(true) }
        val version by model.versionConfig.observeAsState()
        if (version != null) {
            val version = version!!
            if (version.name.compareTo("v${application.appVersionName}") >= 1 && showNewVersionDialog) {
                FMessageDialog(
                    title = "发现新版本 ${version.name}!",
                    confirm = "确定",
                    cancel = "取消",
                    onCancel = {
                        showNewVersionDialog = false
                    },
                    onConfirm = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(version.browserDownloadUrl),
                            )
                        )
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier,
                        content = {
                            item {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = version.body,
                                )
                            }
                        },
                    )
                }
            }
        }

        //获取模块状态
        var moduleHint = StringRes.moduleHintFailed
        if (HookStatus.isEnabled) {
            moduleHint = StringRes.moduleHintSucceeded
        } else if (HookStatus.isExpModuleActive(this)) {
            moduleHint = StringRes.moduleHintSucceeded
        }

        //view
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 模块状态
            FCard(
                modifier = Modifier.padding(bottom = 24.dp, top = 12.dp),
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        content = {
                            Text(
                                modifier = Modifier.align(Alignment.Center),
                                text = moduleHint,
                                style = Themes.nowTypography.body1,
                            )
                        },
                    )
                }
            )

            //模块设置
            FCard(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { toSetting() }
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "提示",
                            tint = Themes.nowColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Column {
                            Text(
                                text = "模块设置已迁移（点击跳转原设置）",
                                style = Themes.nowTypography.body1,
                            )
                            Text(
                                text = "模块设置已迁移至抖音内部，点击抖音左上角侧滑栏，滑动至底部唤起模块设置",
                                style = Themes.nowTypography.overline,
                            )
                        }
                    },
                )
            }

            //数据目录
            FCard(
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 4.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {

                        }
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(id = R.drawable.ic_find_file),
                            contentDescription = "Github",
                            tint = Themes.nowColors.icon,
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text(
                            text = "数据目录: `外置存储器/DCIM/Freedm`",
                            style = Themes.nowTypography.body1,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

            //源码地址
            FCard(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { toBrowse() }
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "Github",
                            tint = Themes.nowColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Column {
                            Text(
                                text = "源码地址",
                                style = Themes.nowTypography.body1,
                            )
                            Text(
                                text = "https://github.com/GangJust/FreedomPlus",
                                style = Themes.nowTypography.overline,
                            )
                        }
                    },
                )
            }

            //交流群
            FCard(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { joinQQGroup() }
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_qq_group),
                            contentDescription = "QQ交流群",
                            tint = Themes.nowColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Column {
                            Text(
                                text = "QQ交流群",
                                style = Themes.nowTypography.body1,
                            )
                            Text(
                                text = "一个随时都可能解散的群聊，加群请合理发言~",
                                style = Themes.nowTypography.overline,
                            )
                        }
                    },
                )
            }

            //打赏
            FCard(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { rewardByAlipay() }
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_spicy_strips),
                            contentDescription = "Github",
                            tint = Themes.nowColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Column {
                            Text(
                                text = "请我吃辣条",
                                style = Themes.nowTypography.body1,
                            )
                            Text(
                                text = "模块免费且开源，打赏与否凭君心情~",
                                style = Themes.nowTypography.overline,
                            )
                        }
                    },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreedomTheme(
                window = window,
                isImmersive = true,
                isDark = false,
                followSystem = false,
            ) {
                Scaffold(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    topBar = { TopBarView() },
                    content = {
                        BoxWithConstraints(
                            modifier = Modifier.padding(it),
                            content = { BodyView() },
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkVersion()
    }

    private fun toSetting() {
        startActivity(
            Intent(
                applicationContext,
                HomeActivity::class.java,
            )
        )
    }

    private fun toBrowse() {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/GangJust/FreedomPlus"),
            )
        )
    }

    private fun joinQQGroup(key: String = "xQKRAH6dNm-F6NDxyn87sX_kvnqEBWxs"): Boolean {
        val intent = Intent()
        intent.data =
            Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key")
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            // 未安装手Q或安装的版本不支持
            showToast("未安装手Q或安装的版本不支持")
            false
        }
    }

    private fun rewardByAlipay() {
        if (!KAppUtils.isAppInstalled(this, "com.eg.android.AlipayGphone")) {
            showToast("谢谢，你没有安装支付宝客户端")
            return
        }
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("alipays://platformapi/startapp?appId=09999988&actionType=toAccount&goBack=NO&amount=3.00&userId=2088022940366251&memo=呐，拿去吃辣条!")
            )
        )
    }

    private fun showToast(text: String) {
        Toast
            .makeText(applicationContext, text, Toast.LENGTH_SHORT)
            .show()
    }
}