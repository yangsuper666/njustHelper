package com.njust.helper.settings;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.njust.helper.R;
import com.njust.helper.databinding.ActivityUpdateBinding;
import com.njust.helper.model.UpdateInfo;
import com.njust.helper.tools.JsonData;
import com.zwb.commonlibs.injection.InjectionHelper;
import com.zwb.commonlibs.injection.IntentInjection;
import com.zwb.commonlibs.injection.ViewInjection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;


public class UpdateActivity extends AppCompatActivity {
    @ViewInjection(R.id.textView1)
    private TextView textView;
    @ViewInjection(R.id.button1)
    private Button button;

    @IntentInjection
    private UpdateInfo updateInfo;
    /**
     * status
     * 0 - 等待
     * 1 - 正在检测更新
     * 2 - 检测更新完成，有更新
     * 3 - 无更新
     * 4 - 检测失败
     * 5 - 下载中
     * 6 - 下载完成，等待更新
     * 7 - 文件原因下载失败
     * 8 - 网络原因下载失败
     */
    private ActivityUpdateBinding binding;

    public static String getButtonText(int status) {
        switch (status) {
            case 0:
            case 3:
            case 4:
                return "检查更新";
            case 1:
                return "正在检测更新……";
            case 2:
            case 7:
            case 8:
                return "立即下载";
            case 5:
                return "正在下载……";
            case 6:
                return "立即安装";
            default:
                return "";
        }
    }

    public static String getMessageText(UpdateInfo updateInfo, int status) {
        switch (status) {
            case 2:
                return updateInfo.toString();
            case 3:
                return "您已经在使用最新版本";
            case 4:
                return "检测失败，请检查网络连接后重试";
            case 5:
                return updateInfo.toString();
            case 6:
                return "下载完成，点击按钮立即安装";
            case 7:
                return "文件存储失败，请检查您的SD卡后重试";
            case 8:
                return "下载失败，请检查网络连接后重试";
            default:
                return "";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        InjectionHelper.injectActivity(this);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_update);
        if (updateInfo != null) {
            binding.setUpdateInfo(updateInfo);
            binding.setStatus(2);
        }
    }

    public void onClick(View view) {
        switch (binding.getStatus()) {
            case 2:
            case 7:
            case 8:
                AsyncTaskCompat.executeParallel(new DownloadTask(updateInfo.getUrl()));
                break;
            case 6:
                startInstall();
                break;
        }
    }

    private void startInstall(){
        File file;
        try {
            file = new File(getExternalCacheDir(), updateInfo.getVersionCode() + "update.apk");
            if (!file.exists()) throw new Exception("更新文件已被删除");
        } catch (Exception e) {
            binding.setStatus(7);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        //新启Task。否则安装过程app退出会导致安装界面也退出，体验不好。
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent);
    }

    private class DownloadTask extends AsyncTask<Void, Integer, Integer> {
        private String url;

        private DownloadTask(String url) {
            this.url = url;
        }

        @Override
        protected void onPreExecute() {
            binding.setStatus(5);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            BufferedOutputStream outputStream = null;
            BufferedInputStream inputStream = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(10000);
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    return JsonData.STATUS_NET_ERROR;
                }
                long contentLength = connection.getContentLength();
                byte[] buffer = new byte[1024];
                inputStream = new BufferedInputStream(connection.getInputStream());
                File file = getExternalCacheDir();
                //此处用STATUS_CAPTCHA_ERROR临时代替文件错误
                if (file == null) return JsonData.STATUS_CAPTCHA_ERROR;
                if (!file.isDirectory()) {
                    if (!file.delete()) return JsonData.STATUS_CAPTCHA_ERROR;
                }
                if (!file.exists()) {
                    if (!file.mkdir()) return JsonData.STATUS_CAPTCHA_ERROR;
                }
                file = new File(getExternalCacheDir(), updateInfo.getVersionCode() + "update.apk");
                if (file.exists()) {
                    if (!file.isDirectory() && file.length() == contentLength) {
                        publishProgress(100);
                        return JsonData.STATUS_SUCCESS;
                    }
                    if (!file.delete()) return JsonData.STATUS_CAPTCHA_ERROR;
                }
                try {
                    outputStream = new BufferedOutputStream(new FileOutputStream(file));
                } catch (FileNotFoundException e) {
                    return JsonData.STATUS_CAPTCHA_ERROR;
                }
                int i;
                long lengthWritten = 0, lastProgress = 0;
                while ((i = inputStream.read(buffer, 0, 1024)) != -1) {
                    outputStream.write(buffer, 0, i);
                    lengthWritten += i;
                    int progress = (int) (lengthWritten * 100 / contentLength);
                    if (progress > lastProgress) {
                        lastProgress = progress;
                        publishProgress(progress);
                    }
                }
                return JsonData.STATUS_SUCCESS;
            } catch (IOException e) {
                e.printStackTrace();
                return JsonData.STATUS_NET_ERROR;
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            binding.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Integer integer) {
            int result = integer;
            if (result == JsonData.STATUS_SUCCESS) {
                binding.setStatus(6);
                startInstall();
            } else if (result == JsonData.STATUS_NET_ERROR) {
                binding.setStatus(8);
            } else if (result == JsonData.STATUS_CAPTCHA_ERROR) {
                binding.setStatus(7);
            }
        }
    }
}
