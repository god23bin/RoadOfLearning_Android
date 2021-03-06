package com.bin23.servicebestpractice.service.task;

import android.os.AsyncTask;
import android.os.Environment;

import com.bin23.servicebestpractice.utils.DownloadListener;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 *  AsyncTask<String, Integer, Integer>
 *      1. String 表示在执行AsyncTask的时候需要传入一个字符串参数给后台任务
 *      2. Integer 表示使用整型数据来作为进度显示单位
 *      3. Integer 表示使用整型数据反馈执行结果
 */
public class DownloadTask extends AsyncTask<String, Integer, Integer> {
    // 4个整型常量表示下载状态
    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;

    private DownloadListener listener;
    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;

    /**
     *
     * @param listener 下载的状态通过这个参数进行回调
     */
    public DownloadTask (DownloadListener listener) {
        this.listener = listener;
    }

    /**
     * 后台执行具体的逻辑
     * @param params
     * @return
     */
    @Override
    protected Integer doInBackground(String... params) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;
        try {
            long downloadedLength = 0;  // 记录已下载的文件长度
            // 从参数中获取下载地址
            String downloadUrl = params[0];
            // 根据地址截取文件名
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
            // 指定下载到的目录，也就是SD卡的Download目录
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            file = new File(directory + fileName);
            // 需要判断是否已存在要下载的文件
            if(file.exists()){
                // 存在的话读取已下载的字节数，这样可以在后面使用断点续传的功能
                downloadedLength = file.length();
            }
            // 调用getContentLength(downloadUrl)方法获取待下载文件的总长度
            long contentLength = getContentLength(downloadUrl);
            // 如果长度为0，说明文件有问题，直接返回TYPE_FAILED 失败
            if(contentLength==0){
                return TYPE_FAILED;
            }else if(contentLength==downloadedLength){
                // 如果已下载字节和文件总字节相等，说明下载完成，返回TYPE_SUCCESS 成功
                return TYPE_SUCCESS;
            }
            // 接着，使用OkHttp发送网络请求
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    // 添加Header，告诉服务器 —— 实现断点下载，指定从哪个字节开始下载
                    .addHeader("RANGE", "字节=" + downloadedLength + "-")
                    .url(downloadUrl)
                    .build();
            // 获取响应
            Response response = client.newCall(request).execute();
            if(response!=null){
                // IO流进行读取，然后写入本地
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file, "rw");
                savedFile.seek(downloadedLength);   // 跳过已下载的字节
                byte[] bytes = new byte[1024];
                int total = 0;
                int len;
                while((len = is.read(bytes))!=-1){
                    // 这个写入的过程中，还要判断用户是否触发暂停或取消的操作，有就进行返回，中断下载
                    if(isCanceled){
                        return TYPE_CANCELED;
                    }else if(isPaused){
                        return TYPE_PAUSED;
                    }else{
                        total += len;
                        savedFile.write(b,0,len);
                        // 实时计算已下载的百分比
                        int progress = (int) ((total + downloadedLength) * 100 / contentLength);
                        // 调用publishProgress(progress)方法进行通知
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(is!=null){
                    is.close();
                }
                if(savedFile!=null){
                    savedFile.close();
                }
                if(isCanceled&&file!=null){
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }

    private long getContentLength(String downloadUrl) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(downloadUrl).build();
        Response response = client.newCall(request).execute();
        if(response!=null && response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.close();
            return contentLength;
        }
        return 0;
    }

    /**
     * 用户界面更新
     * @param values
     */
    @Override
    protected void onProgressUpdate(Integer... values) {
        // 从参数中获取当前下载进度
        int progress = values[0];
        // 然后与上一次的下载进度进行比较，有变化就调用onProgress()方法通知下载进度更新
        if(progress>lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    /**
     * 通知最终下载结果
     * @param status
     */
    @Override
    protected void onPostExecute(Integer status) {
        // 根据参数中传入的状态进行回调
        switch (status){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            default:
                break;
        }
    }

    public void pauseDownload(){
        isPaused = true;
    }

    public void cancelDownload(){
        isCanceled = true;
    }
}
