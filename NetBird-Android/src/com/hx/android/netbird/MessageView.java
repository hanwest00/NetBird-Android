package com.hx.android.netbird;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.hx.android.netbird.views.FileSelectView;
import com.hx.android.netbird.views.FileSelectView.OnFileOrDirSelected;

public class MessageView extends Activity {
	public static String currIp;//当前对话的IP
	public final static int PORT = 6994;//消息端口
	public final static int MSG_MAX_LEN = 60 * 1024;
	private final static int FILE_SELECT_CODE = 0x123222;
	public final static int FROM_TEXTVIEW = 0x133222;
	public final static int TO_TEXTVIEW = 0x133223;
	private TextView titleView;
	private HorizontalScrollView contentView;
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
	private java.util.Queue<String> sendFiles;
	private java.util.Queue<String> receiveFiles;
	private LayoutParams fillWidthParams = new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.WRAP_CONTENT);

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getStringExtra(NetBird.EXTRA_IP) != null
				|| currIp == null)
			currIp = getIntent().getStringExtra(NetBird.EXTRA_IP);
		try {
			this.initViews();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
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
		SimpleDateFormat sf = new SimpleDateFormat(format);
		return sf.format(new java.util.Date());
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
			receiveFiles.add(msg.substring(23));
		}
		
		//记录到数据库
		try {
			NetBird.AddMessageToDB(currIp, NetBird.GetLocalIp(), msg,
					msg.substring(4, 19));
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

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
		// 自动滚动(TextView)
		int offset = this.contentView.getLineCount()
				* this.contentView.getLineHeight()
				- this.contentView.getHeight();
		if (offset > 0)
			this.contentView.scrollTo(0, offset);
	*/
		this.contentView.scrollTo(0, this.contentView.getScrollY());//自动滚动(ListView)
		
	}

	private void initViews() throws InterruptedException {
		setContentView(R.layout.msg_view);

		this.titleView = (TextView) this.findViewById(R.id.msg_title);
		this.titleView.setHeight((int) (getWindowManager().getDefaultDisplay()
				.getHeight() * 0.05f));
		titleView.setText(currIp);

		this.mainLayout = (RelativeLayout) this
				.findViewById(R.id.msg_main_layout);

		this.contentView = (HorizontalScrollView)this.findViewById(R.id.msg_content);
		this.contentView.getLayoutParams().height = (int) (getWindowManager()
				.getDefaultDisplay().getHeight() * 0.85f);
		this.contentLayout = new LinearLayout(this);
		this.contentLayout.setLayoutParams(this.fillWidthParams);
		this.contentLayout.getLayoutParams().width = getWindowManager()
				.getDefaultDisplay().getWidth();
		this.contentLayout.setOrientation(LinearLayout.VERTICAL);
		this.contentView.addView(this.contentLayout);
		//this.contentView.setMovementMethod(ScrollingMovementMethod
				//.getInstance());// set scroll instance(TextView)
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
			this.fileSelectView
					.setOnFileOrDirSelected(new OnFileOrDirSelected() {
						public void selected(List<File> files, String dir) {

							String sss = dir;
							List<File> sssss = files;
							fileSelectLayout.clearAnimation();
							fileSelectLayout
									.startAnimation(fileSelectLayoutShowHiddenAnim);
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
						fileSelectLayout.clearAnimation();//不清除动画的话，只会不显示但是还是存在。清楚动画后设置隐藏才能成功
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
		// todo:发送文件请求
		if(!this.doSendMessage(file.getAbsolutePath(), "FILE:"))
			return;
		sendFiles.add(file.getAbsolutePath());
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
