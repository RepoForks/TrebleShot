package com.genonbeta.TrebleShot.activity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amulyakhare.textdrawable.TextDrawable;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.app.Activity;
import com.genonbeta.TrebleShot.fragment.ApplicationListFragment;
import com.genonbeta.TrebleShot.fragment.ConnectDevicesFragment;
import com.genonbeta.TrebleShot.fragment.FileExplorerFragment;
import com.genonbeta.TrebleShot.fragment.ImageListFragment;
import com.genonbeta.TrebleShot.fragment.MusicListFragment;
import com.genonbeta.TrebleShot.fragment.TextStreamListFragment;
import com.genonbeta.TrebleShot.fragment.TransactionGroupListFragment;
import com.genonbeta.TrebleShot.fragment.VideoListFragment;
import com.genonbeta.android.framework.io.DocumentFile;
import com.genonbeta.TrebleShot.object.NetworkDevice;
import com.genonbeta.TrebleShot.service.CommunicationService;
import com.genonbeta.TrebleShot.service.DeviceScannerService;
import com.genonbeta.TrebleShot.service.WorkerService;
import com.genonbeta.TrebleShot.ui.callback.DetachListener;
import com.genonbeta.TrebleShot.ui.callback.PowerfulActionModeSupport;
import com.genonbeta.TrebleShot.ui.callback.TitleSupport;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.TrebleShot.util.FileUtils;
import com.genonbeta.android.framework.util.Interrupter;
import com.genonbeta.TrebleShot.util.TextUtils;
import com.genonbeta.TrebleShot.util.UpdateUtils;
import com.genonbeta.android.framework.widget.PowerfulActionMode;

import java.io.File;
import java.io.FileNotFoundException;

public class HomeActivity extends Activity implements NavigationView.OnNavigationItemSelectedListener, PowerfulActionModeSupport
{
	public static final String ACTION_OPEN_RECEIVED_FILES = "genonbeta.intent.action.OPEN_RECEIVED_FILES";
	public static final String ACTION_OPEN_ONGOING_LIST = "genonbeta.intent.action.OPEN_ONGOING_LIST";
	public static final String EXTRA_FILE_PATH = "filePath";

	public static final int REQUEST_PERMISSION_ALL = 1;

	private PowerfulActionMode mActionMode;
	private NavigationView mNavigationView;
	private DrawerLayout mDrawerLayout;
	private Fragment mCurrentFragment;
	private Fragment mFragmentConnectDevices;
	private Fragment mFragmentFileExplorer;
	private Fragment mFragmentTransactions;
	private Fragment mFragmentShareText;

	private Fragment mDelayedCommitFragment;

	private long mExitPressTime;

	private boolean mIsStopped = false;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mDrawerLayout = findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.text_navigationDrawerOpen, R.string.text_navigationDrawerClose);
		mDrawerLayout.addDrawerListener(toggle);
		toggle.syncState();

		mActionMode = findViewById(R.id.content_powerful_action_mode);
		mNavigationView = findViewById(R.id.nav_view);

		mNavigationView.setNavigationItemSelectedListener(this);

		mFragmentConnectDevices = Fragment.instantiate(this, ConnectDevicesFragment.class.getName());
		mFragmentFileExplorer = Fragment.instantiate(this, FileExplorerFragment.class.getName());
		mFragmentTransactions = Fragment.instantiate(this, TransactionGroupListFragment.class.getName());
		mFragmentShareText = Fragment.instantiate(this, TextStreamListFragment.class.getName());

		mActionMode.setOnSelectionTaskListener(new PowerfulActionMode.OnSelectionTaskListener()
		{
			@Override
			public void onSelectionTask(boolean started, PowerfulActionMode actionMode)
			{
				toolbar.setVisibility(!started ? View.VISIBLE : View.GONE);
			}
		});

		if (UpdateUtils.hasNewVersion(this))
			highlightUpdater(getDefaultPreferences().getString("availableVersion", null));

		if (!checkRequestedFragment(getIntent()) && !restorePreviousFragment()) {
			changeFragment(mFragmentConnectDevices);
			mNavigationView.setCheckedItem(R.id.menu_activity_main_connect_devices);
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		mIsStopped = false;

		View headerView = mNavigationView.getHeaderView(0);

		if (headerView != null) {
			NetworkDevice localDevice = AppUtils.getLocalDevice(getApplicationContext());

			ImageView imageView = headerView.findViewById(R.id.header_default_device_image);
			TextView deviceNameText = headerView.findViewById(R.id.header_default_device_name_text);
			TextView versionText = headerView.findViewById(R.id.header_default_device_version_text);

			String firstLetters = TextUtils.getLetters(localDevice.nickname, 0);
			TextDrawable drawable = TextDrawable.builder().buildRoundRect(firstLetters.length() > 0 ? firstLetters : "?", ContextCompat.getColor(getApplicationContext(), R.color.networkDeviceRipple), 100);

			imageView.setImageDrawable(drawable);
			deviceNameText.setText(localDevice.nickname);
			versionText.setText(localDevice.versionName);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		// check if no fragment is shown
		if (mDelayedCommitFragment != null)
			changeFragment(mDelayedCommitFragment);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		mIsStopped = true;
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		if (R.id.menu_activity_main_connect_devices == item.getItemId()) {
			changeFragment(mFragmentConnectDevices);
		} else if (R.id.menu_activity_main_file_explorer == item.getItemId()) {
			changeFragment(mFragmentFileExplorer);
		} else if (R.id.menu_activity_main_ongoing_process == item.getItemId()) {
			changeFragment(mFragmentTransactions);
		} else if (R.id.menu_activity_main_share == item.getItemId()) {
			startActivity(new Intent(this, ContentSharingActivity.class));
		} else if (R.id.menu_activity_main_share_text == item.getItemId()) {
			changeFragment(mFragmentShareText);
		} else if (R.id.menu_activity_main_about == item.getItemId()) {
			startActivity(new Intent(this, AboutActivity.class));
		} else if (R.id.menu_activity_main_send_application == item.getItemId()) {
			sendThisApplication();
		} else if (R.id.menu_activity_main_preferences == item.getItemId()) {
			startActivity(new Intent(this, PreferencesActivity.class));
		} else if (R.id.menu_activity_main_exit == item.getItemId()) {
			stopService(new Intent(this, CommunicationService.class));
			stopService(new Intent(this, DeviceScannerService.class));
			stopService(new Intent(this, WorkerService.class));

			finish();
		} else
			return false;

		if (mDrawerLayout != null)
			mDrawerLayout.closeDrawer(GravityCompat.START);

		return true;
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		checkRequestedFragment(intent);
	}

	@Override
	public void onBackPressed()
	{
		if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START))
			mDrawerLayout.closeDrawer(GravityCompat.START);
		else if (mCurrentFragment == null
				|| !(mCurrentFragment instanceof OnBackPressedListener)
				|| !((OnBackPressedListener) mCurrentFragment).onBackPressed()) {
			if ((System.currentTimeMillis() - mExitPressTime) < 2000)
				finish();
			else {
				mExitPressTime = System.currentTimeMillis();
				Toast.makeText(this, R.string.mesg_secureExit, Toast.LENGTH_SHORT).show();
			}
		}
	}

	public boolean changeFragment(final Fragment fragment)
	{
		final Fragment removedFragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);

		if (fragment == removedFragment)
			return false;

		// to prevent possibly removed fragment from being called
		mCurrentFragment = null;

		if (removedFragment != null && removedFragment instanceof DetachListener)
			((DetachListener) removedFragment).onPrepareDetach();

		loadFragment(fragment, true);

		return true;
	}

	public boolean checkRequestedFragment(Intent intent)
	{
		if (intent == null)
			return false;

		if (ACTION_OPEN_RECEIVED_FILES.equals(intent.getAction())) {
			if (intent.hasExtra(EXTRA_FILE_PATH)) {
				Uri directoryUri = intent.getParcelableExtra(EXTRA_FILE_PATH);

				try {
					openFolder(FileUtils.fromUri(getApplicationContext(), directoryUri));
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			} else
				openFolder(null);
		} else if (ACTION_OPEN_ONGOING_LIST.equals(intent.getAction())) {
			changeFragment(mFragmentTransactions);
			mNavigationView.setCheckedItem(R.id.menu_activity_main_ongoing_process);
		} else
			return false;

		return true;
	}

	private void highlightUpdater(String availableVersion)
	{
		MenuItem item = mNavigationView.getMenu().findItem(R.id.menu_activity_main_about);

		item.setChecked(true);
		item.setTitle(R.string.text_newVersionAvailable);
	}

	public PowerfulActionMode getPowerfulActionMode()
	{
		return mActionMode;
	}

	public void loadFragment(final Fragment fragment, final boolean commit)
	{
		new Handler().postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				setTitle(fragment instanceof TitleSupport
						? ((TitleSupport) fragment).getTitle(HomeActivity.this)
						: getString(R.string.text_appName));

				if (commit) {
					FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

					ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

					ft.replace(R.id.content_frame, fragment);

					if (!mIsStopped) {
						ft.commit();

						mDelayedCommitFragment = null;
						mCurrentFragment = fragment;
					} else
						mDelayedCommitFragment = fragment;
				}
			}
		}, 400);
	}

	private void openFolder(@Nullable DocumentFile requestedFolder)
	{
		changeFragment(mFragmentFileExplorer);
		mNavigationView.setCheckedItem(R.id.menu_activity_main_file_explorer);

		if (requestedFolder != null)
			((FileExplorerFragment) mFragmentFileExplorer)
					.goPath(requestedFolder);
	}

	private boolean restorePreviousFragment()
	{
		mCurrentFragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);

		if (mCurrentFragment == null)
			return false;

		loadFragment(mCurrentFragment, false);

		return true;
	}

	private void sendThisApplication()
	{
		new Handler(Looper.myLooper()).post(new Runnable()
		{
			@Override
			public void run()
			{
				try {
					Interrupter interrupter = new Interrupter();

					PackageManager pm = getPackageManager();
					Intent sendIntent = new Intent(Intent.ACTION_SEND);
					PackageInfo packageInfo = pm.getPackageInfo(getApplicationInfo().packageName, 0);

					String fileName = packageInfo.applicationInfo.loadLabel(pm) + "_" + packageInfo.versionName + ".apk";

					DocumentFile storageDirectory = FileUtils.getApplicationDirectory(getApplicationContext(), getDefaultPreferences());
					DocumentFile codeFile = DocumentFile.fromFile(new File(getApplicationInfo().sourceDir));
					DocumentFile cloneFile = storageDirectory.createFile(null, FileUtils.getUniqueFileName(storageDirectory, fileName, true));

					FileUtils.copy(HomeActivity.this, codeFile, cloneFile, interrupter);

					try {
						sendIntent
								.putExtra(ShareActivity.EXTRA_FILENAME_LIST, fileName)
								.putExtra(Intent.EXTRA_STREAM, FileUtils.getSecureUri(HomeActivity.this, cloneFile))
								.setType(cloneFile.getType());

						startActivity(Intent.createChooser(sendIntent, getString(R.string.text_fileShareAppChoose)));
					} catch (IllegalArgumentException e) {
						Toast.makeText(HomeActivity.this, R.string.mesg_providerNotAllowedError, Toast.LENGTH_LONG).show();
						openFolder(storageDirectory);

						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
}
