/*
 * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arialyy.aria.core.download.downloader;

import android.text.TextUtils;
import android.util.Log;
import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadTaskEntity;
import com.arialyy.aria.util.CommonUtil;
import java.io.IOException;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/**
 * Created by Aria.Lao on 2017/7/25.
 * 获取ftp文件信息
 */
class FtpFileInfoThread implements Runnable {

  private final String TAG = "HttpFileInfoThread";
  private DownloadEntity mEntity;
  private DownloadTaskEntity mTaskEntity;
  private int mConnectTimeOut;
  private OnFileInfoCallback mCallback;

  FtpFileInfoThread(DownloadTaskEntity taskEntity, OnFileInfoCallback callback) {
    this.mTaskEntity = taskEntity;
    mEntity = taskEntity.getEntity();
    mConnectTimeOut =
        AriaManager.getInstance(AriaManager.APP).getDownloadConfig().getConnectTimeOut();
    mCallback = callback;
  }

  @Override public void run() {
    FTPClient client = null;
    try {
      client = new FTPClient();
      //ip和端口
      String[] temp = mEntity.getDownloadUrl().split("/");
      String[] pp = temp[2].split(":");
      //String dir = temp[temp.length - 2];
      String fileName = temp[temp.length - 1];
      client.connect(pp[0], Integer.parseInt(pp[1]));
      if (!TextUtils.isEmpty(mTaskEntity.account)) {
        client.login(mTaskEntity.userName, mTaskEntity.userPw);
      } else {
        client.login(mTaskEntity.userName, mTaskEntity.userPw, mTaskEntity.account);
      }
      int reply = client.getReplyCode();
      if (!FTPReply.isPositiveCompletion(reply)) {
        client.disconnect();
        failDownload("无法连接到ftp服务器，错误码为：" + reply);
        return;
      }
      client.setDataTimeout(mConnectTimeOut);
      client.enterLocalPassiveMode();
      client.setFileType(FTP.BINARY_FILE_TYPE);
      FTPFile[] files =
          client.listFiles(CommonUtil.strCharSetConvert(fileName, mTaskEntity.charSet));
      long size = getFileSize(files, client, fileName);
      mEntity.setFileSize(size);
      mTaskEntity.code = reply;
      mEntity.update();
      mTaskEntity.update();
      mCallback.onComplete(mEntity.getDownloadUrl(), reply);
    } catch (IOException e) {
      failDownload(e.getMessage());
    } finally {
      if (client != null) {
        try {
          client.logout();
          client.disconnect();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * 遍历FTP服务器上对应文件或文件夹大小
   *
   * @throws IOException
   */
  private long getFileSize(FTPFile[] files, FTPClient client, String dirName) throws IOException {
    long size = 0;
    String path = dirName + "/";
    for (FTPFile file : files) {
      if (file.isFile()) {
        size += file.getSize();
      } else {
        size += getFileSize(client.listFiles(
            CommonUtil.strCharSetConvert(path + file.getName(), mTaskEntity.charSet)), client,
            path + file.getName());
      }
    }
    return size;
  }

  private void failDownload(String errorMsg) {
    Log.e(TAG, errorMsg);
    if (mCallback != null) {
      mCallback.onFail(mEntity.getDownloadUrl(), errorMsg);
    }
  }
}
