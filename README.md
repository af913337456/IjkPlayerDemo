# IjkPlayerDemo

###功能列表(Function list)
<pre>
&emsp;&emsp;1.支持多种视频播放格式，解码快；
&emsp;&emsp;2.支持多路播放；
&emsp;&emsp;3.能进行视频直播，性能高；
&emsp;&emsp;4.颜值高，还能选择画布比例。
</pre>
        
在上面的基础上已添加如下`功能拓展`:
<pre>
&emsp;&emsp;1.全屏；
&emsp;&emsp;2.上下滑调亮度、声音；
&emsp;&emsp;3.左右滑快进，退；
&emsp;&emsp;4.锁屏与解锁；
&emsp;&emsp;5.像素切换，应对高、超清；
&emsp;&emsp;6.网络状态广播监听及处理；
&emsp;&emsp;.....
</pre>

###怎样使用(How to use)

>使用方法</br>

```java
/** 动态添加，方便多路播放 */
GiraffePlayer player = new GiraffePlayer
                    (
                            this,
                            (LinearLayout) findViewById(R.id.container),
                            "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8"
                    );
```
