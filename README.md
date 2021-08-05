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
    implementation 'com.github.LxzBUG:ScreenShare:1.0.0'
}
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