<p align="center"><img src="https://img-blog.csdnimg.cn/20210804173939408.png" alt="1600" width="15%"/></p>

<p align="center">
    <strong>🌴一行代码实现安卓屏幕采集编码</strong><br>
 <img src="https://img-blog.csdnimg.cn/20210804181338740.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzE1NzQxNjAz,size_16,color_FFFFFF,t_70" width="350"/>
</p>



<p align="center">
<a href="https://jitpack.io/##LxzBUG/ScreenShare"><img src="https://jitpack.io/v/LxzBUG/ScreenShare.svg"/></a>
<img src="https://img.shields.io/badge/language-kotlin-orange.svg"/>
<img src="https://img.shields.io/badge/license-Apache-blue"/>
</p>

## 特点

- 适配安卓高版本
- 使用 MediaCodec 异步硬编码
- 使用 ImageReader 获取屏幕截图数据
- 支持 捕获应用内声音
- 全局内容旋转监听
- 编码信息可配置
- 通知栏显示
- 链式调用

## 安装

在项目根目录的 build.gradle 添加仓库

```groovy
allprojects {
    repositories {
        // ...
        maven { url 'https://jitpack.io' }
    }
}
```

在 module 的 build.gradle 添加依赖

```
dependencies {
    implementation 'com.github.LxzBUG:ScreenShare:1.1.6'
}
```

## 使用

```kotlin
//获取H264数据
ScreenShareKit.init(this).config(screenDataType = EncodeBuilder.SCREEN_DATA_TYPE.H264).onH264(object :H264CallBack{
            override fun onH264(
                buffer: ByteBuffer,
                isKeyFrame: Boolean,
                width: Int,
                height: Int,
                ts: Long
            ) {
              //编码后的的数据
            }
        }).onStart({
            //用户同意采集，开始采集数据
        }).start()

//获取RGBA数据
ScreenShareKit.init(this).config(screenDataType = EncodeBuilder.SCREEN_DATA_TYPE.RGBA).onRGBA(object :RGBACallBack{
            override fun onRGBA(
                rgba: ByteArray,
                width: Int,
                height: Int,
                stride: Int,
                rotation: Int,
                rotationChanged: Boolean
            ) {
                //屏幕截图数据
            }

        }).onStart({
            //用户同意采集，开始采集数据
        }).start()

//开启音频捕获
ScreenShareKit.init(this).config(screenDataType = EncodeBuilder.SCREEN_DATA_TYPE.RGBA,audioCapture = true).onRGBA(object :RGBACallBack{
            override fun onRGBA(
                rgba: ByteArray,
                width: Int,
                height: Int,
                stride: Int,
                rotation: Int,
                rotationChanged: Boolean
            ) {
                //屏幕截图数据
            }

        }).onAudio(object :AudioCallBack{
            override fun onAudio(buffer: ByteArray?, ts: Long) {
                //音频数据
            }

        }).onStart({
            //用户同意采集，开始采集数据
        }).start()

//静音
ScreenShareKit.setMicrophoneMute(true)

//停止采集
ScreenShareKit.stop()
```



## License

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```