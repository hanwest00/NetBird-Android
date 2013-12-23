package com.hx.android.netbird;

import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Audio;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.LinearLayout.LayoutParams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.hx.android.netbird.views.FileSelectView;
import com.hx.android.netbird.views.FileSelectView.OnFileOrDirSelected;

public class MessageView extends Activity {
	public static String currIp;// 当前对话的IP
	public final static int PORT = 6994;// 消息端口
	public static int filePort = 10027;// 文件发送起始端口
	public final static int MSG_MAX_LEN = 60 * 1024;
	private final static int FILE_SELECT_CODE = 0x123222;
	public final static int FROM_TEXTVIEW = 0x133222;
	public final static int TO_TEXTVIEW = 0x133223;
	public static String saveFilePath = "/sdcard/NetBird/Download/";
	private TextView titleView;
	private LinearLayout contentView;
	private LinearLayout contentLayout;
	private EditText editText;
	private Button btn;
	private DatagramSocket socket;
	private Thread fileThread;
	private RelativeLayout mainLayout;
	private LinearLayout fileSelectLayout;
	private Animation fileSelectLayoutShowAnim;
	private Animation fileSelectLayoutShowHiddenAnim;
	private FileSelectView fileSelectView;
	private java.util.Queue<String> sendFiles;// 本机发送的文件队列
	private java.util.Queue<String> receiveFiles;// 当前对话IP发送到本机的文件队列
	private LayoutParams fillWidthParams = new LayoutParams(
			LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);

	private NotificationManager notificationManager;

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		notificationManager = (NotificationManager) this
				.getSystemService(NOTIFICATION_SERVICE);

		if (getIntent().getStringExtra(NetBird.EXTRA_IP) != null
				|| currIp == null)
			currIp = getIntent().getStringExtra(NetBird.EXTRA_IP);
		try {
			this.initViews();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		java.io.File d = new java.io.File(saveFilePath);
		if (!d.exists())
			d.mkdirs();

		IntentFilter intentFliter = new IntentFilter();
		intentFliter.addAction("action.newMessage");
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				// TODO Auto-generated method stub
				bindMessage(intent.getStringExtra(NetBird.EXTRA_MSG));
			}

		}, intentFliter);

		this.bindMessage(getIntent().getStringExtra(NetBird.EXTRA_MSG));
		if (getIntent().getParcelableArrayListExtra(NetBird.EXTRA_MSG_LIST) != null)
			this.bindMessage((ArrayList<String>) getIntent()
					.getParcelableArrayListExtra(NetBird.EXTRA_MSG_LIST).get(0));
		try {
			this.socket = new DatagramSocket();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.btn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// TODO Auto-generated method stub
				try {
					sendMessage();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		this.bindMessage(intent.getStringExtra(NetBird.EXTRA_MSG));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case FILE_SELECT_CODE:
			if (resultCode == RESULT_OK) {
				// Get the Uri of the selected file
				Uri uri = data.getData();
				String path = uri.getPath();
			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem sendFile = menu.add("Send File");
		MenuItem msgRecord = menu.add("Record");

		sendFile.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// TODO 发送文件请求
				showFileDialog();
				return true;
			}
		});

		msgRecord
				.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						// TODO 查看消息记录
						return true;
					}
				});

		return super.onCreateOptionsMenu(menu);
	}

	private void showFileDialog() {
		if (fileSelectLayout.getVisibility() == View.VISIBLE)
			return;
		this.fileSelectLayout.clearAnimation();
		this.fileSelectLayout.startAnimation(fileSelectLayoutShowAnim);
		this.fileSelectLayout.setFocusable(true);
		this.fileSelectLayout.setFocusableInTouchMode(true);
		this.fileSelectLayout.requestFocus();
	}

	public static String GetCurrDate(String format) {
		return new SimpleDateFormat(format).format(new java.util.Date());
	}

	private void bindMessage(ArrayList<String> msgList) {
		if (msgList == null)
			return;
		for (String msg : msgList)
			this.bindMessage(msg);
	}

	private void bindMessage(String msg) {
		if (msg == null || msg.length() < 4)
			return;
		// MSG

		if (msg.startsWith("MSG"))
			this.formatMessageToShow(currIp, msg.substring(23),
					msg.substring(4, 19));
		// FILE
		else if (msg.startsWith("FILE")) {
			// 确保唯一
			if (!receiveFiles.contains(msg.substring(23))) {
				receiveFiles.add(msg.substring(23));
				try {
					this.showFileRequest(currIp, msg.substring(23),
							msg.substring(4, 19));
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchFieldException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		if (msg.startsWith("SFILE")) {
			// todo:同意开始传输文件
		} else if (msg.startsWith("CFILE")) {
			receiveFiles.remove(msg.substring(23));
		} else {

			// 记录到数据库
			try {
				NetBird.AddMessageToDB(currIp, NetBird.GetLocalIp(), msg,
						msg.substring(4, 19));
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// 绑定文件请求到UI
	private void showFileRequest(String from, final String msg, String date)
			throws IllegalArgumentException, SecurityException,
			IllegalAccessException, NoSuchFieldException {
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT));
		layout.setOrientation(LinearLayout.HORIZONTAL);

		LinearLayout layout2 = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT));
		layout.setOrientation(LinearLayout.VERTICAL);

		TextView msgTv = new TextView(this);
		ImageView filePicImg = new ImageView(this);
		filePicImg.setLayoutParams(new LayoutParams(30, 30));
		TextView fileNameTv = new TextView(this);
		Button acceptBtn = new Button(this);
		Button cancelBtn = new Button(this);

		fileNameTv.setText(msg.substring(msg.lastIndexOf("/" + 1)));

		msgTv.setText(java.text.MessageFormat.format("{0}请求发送文件{1}是否接受?",
				currIp, fileNameTv.getText()));

		filePicImg.setImageResource(fileSelectView
				.getImageResourceByFileExt(msg.substring(
						msg.lastIndexOf(".") + 1).toLowerCase()));

		acceptBtn.setText("接收");
		acceptBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String[] tmpArr = msg.split("|");
				if (tmpArr.length != 2)
					return;
				try {
					// 发送同意接收信息
					sendFileAccept(tmpArr[1], Integer.parseInt(tmpArr[0]));
				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});

		cancelBtn.setText("取消");
		cancelBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				String[] tmpArr = msg.split("|");
				if (tmpArr.length != 2)
					return;
				sendFileCancle(tmpArr[1]);
			}

		});

		layout.addView(msgTv);
		layout2.addView(filePicImg);
		layout2.addView(fileNameTv);
		layout.addView(layout2);
		layout.addView(acceptBtn);

		this.contentView.addView(layout);
		this.contentView.scrollTo(0, this.contentView.getScrollY());// 自动滚动(ListView)
		// 取消全屏
		PreferenceManager.getDefaultSharedPreferences(this).edit()
				.putBoolean("fullScreen", false).commit();
	}

	// 绑定消息信息到UI
	private void formatMessageToShow(String from, String msg, String date) {
		StringBuilder sb = new StringBuilder(date);
		sb.append(" ");
		sb.append(from);
		sb.append(" say:\r\n");
		sb.append(msg);
		sb.append("\r\n\r\n");

		TextView tv = new TextView(this);
		tv.setLayoutParams(this.fillWidthParams);
		tv.setId(FROM_TEXTVIEW);
		tv.setText(sb.toString());
		this.contentLayout.addView(tv);
		/*
		 * // 自动滚动(TextView) int offset = this.contentView.getLineCount()
		 * this.contentView.getLineHeight() - this.contentView.getHeight(); if
		 * (offset > 0) this.contentView.scrollTo(0, offset);
		 */
		this.contentView.scrollTo(0, this.contentView.getScrollY());// 自动滚动(ListView)

	}

	private void initViews() throws InterruptedException {
		setContentView(R.layout.msg_view);

		this.titleView = (TextView) this.findViewById(R.id.msg_title);
		this.titleView.setHeight((int) (getWindowManager().getDefaultDisplay()
				.getHeight() * 0.05f));
		titleView.setText(currIp);

		this.mainLayout = (RelativeLayout) this
				.findViewById(R.id.msg_main_layout);

		this.contentView = (LinearLayout) this.findViewById(R.id.msg_content);
		this.contentView.getLayoutParams().height = (int) (getWindowManager()
				.getDefaultDisplay().getHeight() * 0.85f);
		this.contentLayout = new LinearLayout(this);
		this.contentLayout.setLayoutParams(this.fillWidthParams);
		this.contentLayout.getLayoutParams().width = getWindowManager()
				.getDefaultDisplay().getWidth();
		this.contentLayout.setOrientation(LinearLayout.VERTICAL);
		this.contentView.addView(this.contentLayout);
		// this.contentView.setMovementMethod(ScrollingMovementMethod
		// .getInstance());// set scroll instance(TextView)
		this.editText = (EditText) this.findViewById(R.id.msg_edit);
		this.editText.setWidth((int) (this.getWindowManager()
				.getDefaultDisplay().getWidth() * 0.8f));
		this.editText.setHeight((int) (getWindowManager().getDefaultDisplay()
				.getHeight() * 0.1f));
		this.btn = (Button) this.findViewById(R.id.msg_btn);
		this.btn.setWidth((int) (this.getWindowManager().getDefaultDisplay()
				.getWidth() * 0.2f));
		this.btn.setHeight((int) (getWindowManager().getDefaultDisplay()
				.getHeight() * 0.1f));

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		lp.height = (int) (getWindowManager().getDefaultDisplay().getHeight() * 0.95);
		lp.width = (int) (getWindowManager().getDefaultDisplay().getWidth() * 0.95);
		lp.topMargin = getWindowManager().getDefaultDisplay().getHeight()
				- lp.height;

		this.fileSelectLayoutShowAnim = new TranslateAnimation(0, 0, lp.height,
				0);
		// 加速动画执行
		this.fileSelectLayoutShowAnim
				.setInterpolator(AnimationUtils.loadInterpolator(this,
						android.R.anim.accelerate_interpolator));
		this.fileSelectLayoutShowAnim.setFillAfter(true);
		this.fileSelectLayoutShowAnim.setDuration(200);

		this.fileSelectLayoutShowHiddenAnim = new TranslateAnimation(0, 0, 0,
				lp.height);
		this.fileSelectLayoutShowHiddenAnim
				.setInterpolator(AnimationUtils.loadInterpolator(this,
						android.R.anim.accelerate_interpolator));
		this.fileSelectLayoutShowHiddenAnim.setFillAfter(true);
		this.fileSelectLayoutShowHiddenAnim.setDuration(200);

		this.fileSelectLayout = new LinearLayout(this);
		this.fileSelectLayout.setBackgroundColor(Color.alpha(100));
		this.fileSelectLayout.setGravity(Gravity.CENTER_HORIZONTAL);
		this.fileSelectLayout.setVisibility(View.GONE);
		this.fileSelectLayout.setLayoutParams(lp);

		/*
		 * TextView flieTitle = new TextView(this);
		 * flieTitle.setGravity(Gravity.CENTER_HORIZONTAL);
		 * flieTitle.setText("select file"); flieTitle.setWidth(lp.width);
		 * flieTitle.setBackgroundColor(Color.GRAY);
		 * this.fileSelectLayout.addView(flieTitle);
		 */

		try {
			this.fileSelectView = new FileSelectView(this, lp.width, lp.height);
			// 绑定文件选择控件选择完成事件
			this.fileSelectView
					.setOnFileOrDirSelected(new OnFileOrDirSelected() {
						public void selected(final List<File> files, String dir) {

							if (files.size() < 1)
								return;

							// 是否发送文件提示框
							new AlertDialog.Builder(MessageView.this)
									.setTitle("提示")
									.setMessage(
											java.text.MessageFormat.format(
													"是否发送{0}?", files.get(0)
															.getName()))
									.setNeutralButton(
											"发送",
											new DialogInterface.OnClickListener() {
												public void onClick(
														DialogInterface dialog,
														int which) {

													try {
														sendFileRequest(files
																.get(0));// 当前版本只发送单个文件
													} catch (IOException e) {
														// TODO
														// Auto-generated
														// catch block
														e.printStackTrace();
													}

													// 隐藏文件选择控件
													fileSelectLayout
															.clearAnimation();
													fileSelectLayout
															.startAnimation(fileSelectLayoutShowHiddenAnim);
												}
											})
									.setNegativeButton(
											"取消",
											new DialogInterface.OnClickListener() {
												public void onClick(
														DialogInterface dialog,
														int which) {
													dialog.dismiss();
												}
											}).create().show();
						}
					});
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.fileSelectLayout.addView(this.fileSelectView);

		this.mainLayout.addView(this.fileSelectLayout);
		this.fileSelectLayoutShowAnim
				.setAnimationListener(new AnimationListener() {

					public void onAnimationEnd(Animation animation) {
						// TODO Auto-generated method stub
					}

					public void onAnimationRepeat(Animation animation) {
						// TODO Auto-generated method stub

					}

					public void onAnimationStart(Animation animation) {
						// TODO Auto-generated method stub
						fileSelectLayout.setVisibility(View.VISIBLE);
					}
				});

		this.fileSelectLayoutShowHiddenAnim
				.setAnimationListener(new AnimationListener() {

					public void onAnimationEnd(Animation animation) {
						// TODO Auto-generated method stub
						fileSelectLayout.clearAnimation();// 不清除动画的话，只会不显示但是还是存在。清楚动画后设置隐藏才能成功
						fileSelectLayout.setVisibility(View.GONE);
					}

					public void onAnimationRepeat(Animation animation) {
						// TODO Auto-generated method stub

					}

					public void onAnimationStart(Animation animation) {
						// TODO Auto-generated method stub
						fileSelectLayout.setVisibility(View.VISIBLE);
					}
				});
	}

	private void sendMessage() throws IOException {
		if (!this.doSendMessage(this.editText.getText().toString(), "MSG:"))
			return;
		this.editText.setText("");
		// 隐藏虚拟键盘
		((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
				.hideSoftInputFromWindow(this.editText.getWindowToken(), 0);
	}

	private boolean doSendMessage(String msg, String msgHeader)
			throws IOException {
		if (currIp == null || currIp == "" || msg.length() < 1
				|| msg.length() > MSG_MAX_LEN)
			return false;
		String date = GetCurrDate("yyyy-MM-dd HH:mm:ss");
		StringBuilder sb = new StringBuilder(msgHeader);
		sb.append(date);
		sb.append(msg);
		byte[] buffer = sb.toString().getBytes();
		DatagramPacket dp = new DatagramPacket(buffer, buffer.length,
				Inet4Address.getByName(this.currIp), PORT);
		this.socket.send(dp);
		this.formatMessageToShow("", this.editText.getText().toString(), date);

		// 保存记录到db
		NetBird.AddMessageToDB(NetBird.GetLocalIp(), this.currIp,
				sb.toString(), date);
		return true;
	}

	private void sendFileRequest(File file) throws IOException {
		// 确保唯一
		if (sendFiles.contains(file.getAbsolutePath()))
			return;

		// format: FILE:[length]|[fileFullPath]
		StringBuilder sb = new StringBuilder(String.valueOf(file.length()));
		sb.append("|");
		sb.append(file.getAbsolutePath());
		if (!this.doSendMessage(sb.toString(), "FILE:"))
			return;
		sendFiles.add(file.getAbsolutePath());
	}

	private void sendFileCancle(String file) {
		try {
			this.doSendMessage(file, "CFILE:");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// 发送同意接受文件的信息
	private void sendFileAccept(final String file, final int length)
			throws IOException {
		// format: SFILE:[port]|[fileFullPath]
		StringBuilder sb = new StringBuilder(filePort);
		sb.append("|");
		sb.append(file);
		if (!this.doSendMessage(sb.toString(), "SFILE:"))
			return;

		// 显示文件接受开始通知
		Notification fileNoti = new Notification();
		fileNoti.flags |= Notification.FLAG_ONGOING_EVENT;// 将此通知放到通知栏的"Ongoing"即"正在运行"组中
		fileNoti.flags |= Notification.FLAG_NO_CLEAR; // 表明在点击了通知栏中的"清除通知"后，此通知不清除，经常与FLAG_ONGOING_EVENT一起使用
		int imgRes = R.drawable.file;
		
		try {
			imgRes = fileSelectView.getImageResourceByFileExt(file
					.substring(file.lastIndexOf(".") + 1));
		} catch (IllegalArgumentException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		} catch (SecurityException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		} catch (IllegalAccessException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		} catch (NoSuchFieldException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}

		fileNoti.icon = imgRes;

		Intent intent = new Intent(MessageView.this, MessageView.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);

		fileNoti.contentView = new RemoteViews(getPackageName(),
				R.layout.file_notice_view);
		fileNoti.contentIntent = pIntent;
		try {
			fileNoti.contentView.setImageViewResource(R.id.file_notic_pic,
					imgRes);
			fileNoti.contentView.setTextViewText(R.id.file_notic_info, "");
			fileNoti.contentView.setProgressBar(R.id.file_notic_proc, length, 0, false);
			notificationManager.notify(0, fileNoti);//todo:需要ID
		} catch (IllegalArgumentException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (SecurityException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		// 新开线程监听文件发送
		new Thread(new Runnable() {
			public void run() {
				String fileName = file.substring(file.lastIndexOf("/") + 1);
				if (fileName.equals(""))
					return;

				File newFile = new File(saveFilePath + fileName);
				if (newFile.exists())
					newFile = new File(saveFilePath
							+ new java.util.Date().getTime() + fileName);

				FileOutputStream fout = null;

				try {
					fout = new FileOutputStream(newFile);
				} catch (FileNotFoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				byte[] buffer = new byte[MSG_MAX_LEN];
				DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
				DatagramSocket fileDs = null;
				try {
					fileDs = new DatagramSocket(filePort);
					filePort++;
					fileDs.setSoTimeout(1000);
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				int acceptLength = 0;
				while (true) {
					try {
						if (acceptLength >= length)
							break;// 接受完成

						fileDs.receive(dp);
						if (dp.getLength() > 0)
							fout.write(buffer, 0, dp.getLength());
						acceptLength += dp.getLength();
						Thread.sleep(10);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						continue;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}).start();

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (fileSelectLayout.getVisibility() == View.VISIBLE) {
				fileSelectLayout.clearAnimation();
				fileSelectLayout.startAnimation(fileSelectLayoutShowHiddenAnim);
				this.editText.requestFocus();
				return true;
			} else
				return super.onKeyDown(keyCode, event);
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}
