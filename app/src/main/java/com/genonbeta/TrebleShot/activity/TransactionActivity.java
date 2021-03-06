package com.genonbeta.TrebleShot.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.transition.TransitionManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.adapter.DefaultFragmentPagerAdapter;
import com.genonbeta.TrebleShot.adapter.PathResolverRecyclerAdapter;
import com.genonbeta.TrebleShot.adapter.TransactionListAdapter;
import com.genonbeta.TrebleShot.adapter.TransferAssigneeListAdapter;
import com.genonbeta.TrebleShot.app.Activity;
import com.genonbeta.TrebleShot.config.Keyword;
import com.genonbeta.TrebleShot.database.AccessDatabase;
import com.genonbeta.TrebleShot.fragment.TransactionListFragment;
import com.genonbeta.TrebleShot.fragment.TransferAssigneeListFragment;
import com.genonbeta.TrebleShot.object.NetworkDevice;
import com.genonbeta.TrebleShot.object.TransferGroup;
import com.genonbeta.TrebleShot.object.TransferObject;
import com.genonbeta.TrebleShot.service.CommunicationService;
import com.genonbeta.TrebleShot.service.WorkerService;
import com.genonbeta.TrebleShot.ui.callback.PowerfulActionModeSupport;
import com.genonbeta.TrebleShot.ui.callback.TitleSupport;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.TrebleShot.util.DynamicNotification;
import com.genonbeta.TrebleShot.util.FileUtils;
import com.genonbeta.TrebleShot.util.TextUtils;
import com.genonbeta.TrebleShot.util.TransferUtils;
import com.genonbeta.android.database.CursorItem;
import com.genonbeta.android.database.SQLQuery;
import com.genonbeta.android.database.SQLiteDatabase;
import com.genonbeta.android.framework.io.DocumentFile;
import com.genonbeta.android.framework.io.LocalDocumentFile;
import com.genonbeta.android.framework.ui.callback.SnackbarSupport;
import com.genonbeta.android.framework.widget.PowerfulActionMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by: veli
 * Date: 5/23/17 1:43 PM
 */

public class TransactionActivity
		extends Activity
		implements PowerfulActionModeSupport
{
	public static final String TAG = TransactionActivity.class.getSimpleName();
	public static final int JOB_FILE_FIX = 1;

	public static final int TASK_CRUNCH_DATA = 1;

	public static final String ACTION_LIST_TRANSFERS = "com.genonbeta.TrebleShot.action.LIST_TRANSFERS";
	public static final String EXTRA_GROUP_ID = "extraGroupId";

	private TransferGroup mGroup;
	private BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (AccessDatabase.ACTION_DATABASE_CHANGE.equals(intent.getAction())
					&& intent.hasExtra(AccessDatabase.EXTRA_TABLE_NAME)
					&& intent.hasExtra(AccessDatabase.EXTRA_CHANGE_TYPE)
					&& AccessDatabase.TABLE_TRANSFERGROUP.equals(intent.getStringExtra(AccessDatabase.EXTRA_TABLE_NAME))) {
				reconstructGroup();
				updateCalculations();
			}
		}
	};

	private TransferGroup.Index mTransactionIndex = new TransferGroup.Index();
	private DefaultFragmentPagerAdapter mPagerAdapter;
	private PowerfulActionMode mPowafulActionMode;
	private MenuItem mStartMenu;
	private MenuItem mRetryMenu;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_transaction);

		mPowafulActionMode = findViewById(R.id.activity_transaction_action_mode);
		mPagerAdapter = new DefaultFragmentPagerAdapter(this, getSupportFragmentManager());

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		final TabLayout tabLayout = findViewById(R.id.activity_transaction_tab_layout);
		final ViewPager viewPager = findViewById(R.id.activity_transaction_view_pager);
		final TransactionDetailsFragment detailsFragment = new TransactionDetailsFragment();
		final TransferAssigneeListFragment assigneeListFragment = new TransferAssigneeListFragment();
		final TransactionExplorerFragment transactionFragment = new TransactionExplorerFragment();

		if (ACTION_LIST_TRANSFERS.equals(getIntent().getAction()) && getIntent().hasExtra(EXTRA_GROUP_ID)) {
			TransferGroup group = new TransferGroup(getIntent().getLongExtra(EXTRA_GROUP_ID, -1));

			try {
				getDatabase().reconstruct(group);
				mGroup = group;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (mGroup == null)
			finish();
		else {
			Bundle detailsFragmentArgs = new Bundle();
			detailsFragmentArgs.putLong(TransactionDetailsFragment.ARG_GROUP_ID, mGroup.groupId);
			detailsFragment.setArguments(detailsFragmentArgs);

			Bundle assigneeFragmentArgs = new Bundle();
			assigneeFragmentArgs.putLong(TransferAssigneeListFragment.ARG_GROUP_ID, mGroup.groupId);
			assigneeListFragment.setArguments(assigneeFragmentArgs);

			Bundle transactionFragmentArgs = new Bundle();
			transactionFragmentArgs.putLong(TransactionExplorerFragment.ARG_GROUP_ID, mGroup.groupId);
			transactionFragmentArgs.putString(TransactionExplorerFragment.ARG_PATH, null);
			transactionFragment.setArguments(transactionFragmentArgs);

			tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);

			mPagerAdapter.add(detailsFragment, tabLayout);
			mPagerAdapter.add(transactionFragment, tabLayout);
			mPagerAdapter.add(assigneeListFragment, tabLayout);

			viewPager.setAdapter(mPagerAdapter);
			viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

			tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener()
			{
				@Override
				public void onTabSelected(TabLayout.Tab tab)
				{
					viewPager.setCurrentItem(tab.getPosition());
				}

				@Override
				public void onTabUnselected(final TabLayout.Tab tab)
				{

				}

				@Override
				public void onTabReselected(TabLayout.Tab tab)
				{

				}
			});

			mPowafulActionMode.setOnSelectionTaskListener(new PowerfulActionMode.OnSelectionTaskListener()
			{
				@Override
				public void onSelectionTask(boolean started, PowerfulActionMode actionMode)
				{
					toolbar.setVisibility(!started ? View.VISIBLE : View.GONE);
				}
			});
		}

		bindService(new Intent(TransactionActivity.this, CommunicationService.class)
				.setAction(CommunicationService.ACTION_SERVICE_CONNECTION_TRANSFER_QUEUE), new ServiceConnection()
		{
			@Override
			public void onServiceConnected(ComponentName name, IBinder service)
			{
				Log.d(TAG, "The service " + name.getClassName() + " has connected; binder:" + service.getClass().getCanonicalName());

				Parcel parcelMsg = Parcel.obtain();
				Parcel replyMsg = Parcel.obtain();

				try {
					Log.d(TAG, "The result is: " + service.transact(IBinder.FIRST_CALL_TRANSACTION, parcelMsg, replyMsg, 0));
					Log.d(TAG, "Everything is just like they are supposed to be " + replyMsg.readString());
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name)
			{
				Log.d(TAG, "The service " + name.getClassName() + " has disconnected");
			}
		}, 0);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		registerReceiver(mReceiver, new IntentFilter(AccessDatabase.ACTION_DATABASE_CHANGE));
		reconstructGroup();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		unregisterReceiver(mReceiver);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.actions_transaction, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		mStartMenu = menu.findItem(R.id.actions_transaction_resume_all);
		mRetryMenu = menu.findItem(R.id.actions_transaction_retry_all);

		if (!getIndex().calculated)
			updateCalculations();
		else
			showMenus();

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();

		if (id == android.R.id.home)
			onBackPressed();
		else if (id == R.id.actions_transaction_resume_all) {
			resumeReceiving();
		} else if (id == R.id.actions_transaction_retry_all) {
			ContentValues contentValues = new ContentValues();

			contentValues.put(AccessDatabase.FIELD_TRANSFER_FLAG, TransferObject.Flag.PENDING.toString());

			getDatabase().update(new SQLQuery.Select(AccessDatabase.TABLE_TRANSFER)
					.setWhere(AccessDatabase.FIELD_TRANSFER_GROUPID + "=? AND "
									+ AccessDatabase.FIELD_TRANSFER_FLAG + "=? AND "
									+ AccessDatabase.FIELD_TRANSFER_TYPE + "=?",
							String.valueOf(mGroup.groupId),
							TransferObject.Flag.INTERRUPTED.toString(),
							TransferObject.Type.INCOMING.toString()), contentValues);

			createSnackbar(R.string.mesg_retryAllInfo)
					.show();
		} else
			return super.onOptionsItemSelected(item);

		return true;
	}

	private Snackbar createSnackbar(int resId, Object... objects)
	{
		return Snackbar.make(findViewById(R.id.activity_transaction_view_pager), getString(resId, objects), Snackbar.LENGTH_LONG);
	}

	@Nullable
	public TransferGroup getGroup()
	{
		return mGroup;
	}

	public TransferGroup.Index getIndex()
	{
		return mTransactionIndex;
	}

	@Override
	public PowerfulActionMode getPowerfulActionMode()
	{
		return mPowafulActionMode;
	}

	public void reconstructGroup()
	{
		try {
			getDatabase().reconstruct(mGroup);
		} catch (Exception e) {
			e.printStackTrace();
			finish();
		}
	}

	private void showMenus()
	{
		boolean hasIncoming = getIndex().incomingCount > 0;

		mStartMenu.setVisible(hasIncoming);
		mRetryMenu.setVisible(hasIncoming);

		if (mPagerAdapter != null && mPagerAdapter.getCount() > 0) {
			TransactionDetailsFragment fragment = (TransactionDetailsFragment) mPagerAdapter.getItem(0);

			if (fragment.isAdded())
				fragment.updateViewState(getIndex());
		}

		setTitle(getResources().getQuantityString(R.plurals.text_files,
				getIndex().incomingCount + getIndex().outgoingCount,
				getIndex().incomingCount + getIndex().outgoingCount));
	}

	private void resumeReceiving()
	{
		SQLQuery.Select select = new SQLQuery.Select(AccessDatabase.TABLE_TRANSFERASSIGNEE)
				.setWhere(AccessDatabase.FIELD_TRANSFERASSIGNEE_GROUPID + "=?", String.valueOf(mGroup.groupId));

		ArrayList<TransferAssigneeListAdapter.ShowingAssignee> assignees = getDatabase().castQuery(select, TransferAssigneeListAdapter.ShowingAssignee.class, new SQLiteDatabase.CastQueryListener<TransferAssigneeListAdapter.ShowingAssignee>()
		{
			@Override
			public void onObjectReconstructed(SQLiteDatabase db, CursorItem item, TransferAssigneeListAdapter.ShowingAssignee object)
			{
				object.device = new NetworkDevice(object.deviceId);
				object.connection = new NetworkDevice.Connection(object);

				try {
					db.reconstruct(object.device);
					db.reconstruct(object.connection);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		if (assignees.size() == 0) {
			createSnackbar(R.string.mesg_noReceiverOrSender)
					.show();
			return;
		}

		final TransferAssigneeListAdapter.ShowingAssignee assignee = assignees.get(0);

		try {
			getDatabase().reconstruct(new NetworkDevice.Connection(assignee));
			TransferUtils.resumeTransfer(getApplicationContext(), mGroup, assignee);
		} catch (Exception e) {
			e.printStackTrace();

			createSnackbar(R.string.mesg_transferConnectionNotSetUpFix)
					.setAction(R.string.butn_setUp, new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							TransferUtils.changeConnection(TransactionActivity.this, getDatabase(), mGroup, assignee.device, new TransferUtils.ConnectionUpdatedListener()
							{
								@Override
								public void onConnectionUpdated(NetworkDevice.Connection connection, TransferGroup.Assignee assignee)
								{
									createSnackbar(R.string.mesg_connectionUpdated, TextUtils.getAdapterName(getApplicationContext(), connection))
											.show();
								}
							});
						}
					}).show();
		}
	}

	public void updateCalculations()
	{
		new CrunchLatestDataTask(new CrunchLatestDataTask.PostExecuteListener()
		{
			@Override
			public void onPostExecute()
			{
				showMenus();
			}
		}).execute(this);
	}

	public static void startInstance(Context context, long groupId)
	{
		context.startActivity(new Intent(context, TransactionActivity.class)
				.setAction(ACTION_LIST_TRANSFERS)
				.putExtra(EXTRA_GROUP_ID, groupId)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
	}

	private static class TransactionPathResolverRecyclerAdapter extends PathResolverRecyclerAdapter<String>
	{
		public TransactionPathResolverRecyclerAdapter(Context context)
		{
			super(context);
		}

		public void goTo(String[] paths)
		{
			getList().clear();

			StringBuilder mergedPath = new StringBuilder();

			getList().add(new Holder.Index<>(getContext().getString(R.string.text_home), R.drawable.ic_home_black_24dp, (String) null));

			if (paths != null)
				for (String path : paths) {
					if (path.length() == 0)
						continue;

					if (mergedPath.length() > 0)
						mergedPath.append(File.separator);

					mergedPath.append(path);

					getList().add(new Holder.Index<>(path, mergedPath.toString()));
				}
		}
	}

	public static class TransactionDetailsFragment
			extends com.genonbeta.android.framework.app.Fragment
			implements TitleSupport, SnackbarSupport, com.genonbeta.android.framework.app.FragmentImpl
	{
		public static final String ARG_GROUP_ID = "groupId";

		public static final int REQUEST_CHOOSE_FOLDER = 1;

		private View mRemoveView;
		private View mShowFiles;
		private View mSaveTo;
		private View mButtonFourth;

		private TransferGroup mHeldGroup;

		@Nullable
		@Override
		public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
		{
			View view = inflater.inflate(R.layout.layout_transaction_details, container, false);

			mRemoveView = view.findViewById(R.id.layout_transaction_details_remove);
			mShowFiles = view.findViewById(R.id.layout_transaction_details_show_files);
			mSaveTo = view.findViewById(R.id.layout_transaction_details_save_to);
			mButtonFourth = view.findViewById(R.id.layout_transaction_details_button_fourth);

			mRemoveView.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());

					dialog.setTitle(R.string.ques_removeAll);
					dialog.setMessage(R.string.text_removeCertainPendingTransfersSummary);

					dialog.setNegativeButton(R.string.butn_cancel, null);
					dialog.setPositiveButton(R.string.butn_removeAll, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							AppUtils.getDatabase(getContext()).remove(getTransferGroup());
						}
					});

					dialog.show();
				}
			});

			mSaveTo.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					startActivityForResult(new Intent(getActivity(), FilePickerActivity.class)
							.setAction(FilePickerActivity.ACTION_CHOOSE_DIRECTORY), REQUEST_CHOOSE_FOLDER);
				}
			});

			mShowFiles.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					startActivity(new Intent(getActivity(), HomeActivity.class)
							.setAction(HomeActivity.ACTION_OPEN_RECEIVED_FILES)
							.putExtra(HomeActivity.EXTRA_FILE_PATH, FileUtils.getSavePath(getContext(), AppUtils.getDefaultPreferences(getContext()), getTransferGroup()).getUri()));
				}
			});

			return view;
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent data)
		{
			super.onActivityResult(requestCode, resultCode, data);

			if (data != null) {
				if (resultCode == Activity.RESULT_OK) {
					switch (requestCode) {
						case REQUEST_CHOOSE_FOLDER:
							if (data.hasExtra(FilePickerActivity.EXTRA_CHOSEN_PATH)) {
								final Uri selectedPath = data.getParcelableExtra(FilePickerActivity.EXTRA_CHOSEN_PATH);

								if (selectedPath.toString().equals(getTransferGroup().savePath)) {
									createSnackbar(R.string.mesg_pathSameError).show();
								} else {
									AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

									builder.setTitle(R.string.ques_checkOldFiles);
									builder.setMessage(R.string.text_checkOldFiles);

									builder.setNeutralButton(R.string.butn_cancel, null);
									builder.setNegativeButton(R.string.butn_reject, new DialogInterface.OnClickListener()
									{
										@Override
										public void onClick(DialogInterface dialogInterface, int i)
										{
											updateSavePath(selectedPath.toString());
										}
									});

									builder.setPositiveButton(R.string.butn_accept, new DialogInterface.OnClickListener()
									{
										@Override
										public void onClick(DialogInterface dialogInterface, int i)
										{
											WorkerService.run(getContext(), new WorkerService.NotifiableRunningTask(TAG, JOB_FILE_FIX)
											{
												@Override
												public void onUpdateNotification(DynamicNotification dynamicNotification, UpdateType updateType)
												{
													switch (updateType) {
														case Started:
															dynamicNotification.setSmallIcon(R.drawable.ic_compare_arrows_white_24dp)
																	.setContentText(getString(R.string.mesg_organizingFiles));
															break;
														case Done:
															dynamicNotification.setContentText(getString(R.string.text_movedCacheFiles));
															break;
													}
												}

												@Override
												public void onRun()
												{
													ArrayList<TransferObject> checkList = AppUtils.getDatabase(getContext()).
															castQuery(new SQLQuery.Select(AccessDatabase.TABLE_TRANSFER)
																	.setWhere(AccessDatabase.FIELD_TRANSFER_GROUPID + "=? AND "
																					+ AccessDatabase.FIELD_TRANSFER_TYPE + "=? AND "
																					+ AccessDatabase.FIELD_TRANSFER_FLAG + " != ?",
																			String.valueOf(getTransferGroup().groupId), TransferObject.Type.INCOMING.toString(), TransferObject.Flag.PENDING.toString()), TransferObject.class);

													TransferGroup pseudoGroup = new TransferGroup(getTransferGroup().groupId);

													try {
														// Illustrate new change to build the structure accordingly
														AppUtils.getDatabase(getContext()).reconstruct(pseudoGroup);
														pseudoGroup.savePath = selectedPath.toString();

														for (TransferObject transferObject : checkList) {
															if (getInterrupter().interrupted())
																break;

															try {
																DocumentFile file = FileUtils.getIncomingPseudoFile(getContext(), AppUtils.getDefaultPreferences(getContext()), transferObject, getTransferGroup(), false);
																DocumentFile pseudoFile = FileUtils.getIncomingPseudoFile(getContext(), AppUtils.getDefaultPreferences(getContext()), transferObject, pseudoGroup, true);

																if (file.canRead())
																	FileUtils.move(getContext(), file, pseudoFile, getInterrupter());

																file.delete();
															} catch (IOException e) {
																e.printStackTrace();
															}
														}

														updateSavePath(selectedPath.toString());
													} catch (Exception e) {
														e.printStackTrace();
													}
												}
											});
										}
									});

									builder.show();
								}
							}

							break;
					}
				}
			}
		}

		public void applyViewChanges(TransferGroup.Index index)
		{
			if (getView() == null)
				return;

			View notEnoughSpaceWarning = getView().findViewById(R.id.layout_transaction_details_not_enough_space);
			TextView incomingSize = getView().findViewById(R.id.layout_transaction_details_incoming_size);
			TextView outgoingSize = getView().findViewById(R.id.layout_transaction_details_outgoing_size);
			TextView availableDisk = getView().findViewById(R.id.layout_transaction_details_available_disk_space);
			TextView savePath = getView().findViewById(R.id.layout_transaction_details_save_path);

			DocumentFile storageFile = FileUtils.getSavePath(getContext(), AppUtils.getDefaultPreferences(getContext()), getTransferGroup());
			Resources resources = getContext().getResources();

			notEnoughSpaceWarning.setVisibility(storageFile instanceof LocalDocumentFile
					&& ((LocalDocumentFile) storageFile).getFile().getFreeSpace() < index.incoming
					? View.VISIBLE
					: View.GONE);

			incomingSize.setText(getContext().getString(R.string.mode_itemCountedDetailed,
					resources.getQuantityString(R.plurals.text_files, index.incomingCount, index.incomingCount),
					FileUtils.sizeExpression(index.incoming, false)));

			outgoingSize.setText(getContext().getString(R.string.mode_itemCountedDetailed,
					resources.getQuantityString(R.plurals.text_files, index.outgoingCount, index.outgoingCount),
					FileUtils.sizeExpression(index.outgoing, false)));

			availableDisk.setText(storageFile instanceof LocalDocumentFile
					? FileUtils.sizeExpression(((LocalDocumentFile) storageFile).getFile().getFreeSpace(), false)
					: getContext().getString(R.string.text_unknown));

			savePath.setText(storageFile.getUri().toString());
		}

		@Override
		public CharSequence getTitle(Context context)
		{
			return context.getString(R.string.text_transactionDetails);
		}

		public TransferGroup getTransferGroup()
		{
			if (mHeldGroup == null) {
				mHeldGroup = new TransferGroup(getArguments().getLong(ARG_GROUP_ID, -1));

				try {
					AppUtils.getDatabase(getContext()).reconstruct(mHeldGroup);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			return mHeldGroup;
		}

		public void updateSavePath(String selectedPath)
		{
			TransferGroup group = getTransferGroup();

			group.savePath = selectedPath;
			AppUtils.getDatabase(getContext()).publish(group);

			getActivity().runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					createSnackbar(R.string.mesg_pathSaved).show();
				}
			});
		}

		public void updateViewState(TransferGroup.Index index)
		{
			boolean isIncoming = index.incomingCount > 0;

			applyViewChanges(index);

			mShowFiles.setVisibility(isIncoming ? View.VISIBLE : View.GONE);
			mSaveTo.setVisibility(isIncoming ? View.VISIBLE : View.GONE);

			if (Keyword.Flavor.googlePlay.equals(AppUtils.getBuildFlavor())
					&& (index.outgoingCountCompleted + index.incomingCountCompleted) == (index.incomingCount + index.outgoingCount)) {
				mButtonFourth.setVisibility(View.VISIBLE);
				mButtonFourth.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						try {
							startActivity(new Intent(getContext(), Class.forName("com.genonbeta.TrebleShot.activity.DonationActivity")));
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}
					}
				});
			}

			TransitionManager.beginDelayedTransition((ViewGroup) getView().findViewById(R.id.layout_transaction_details_layout_actions));
			TransitionManager.beginDelayedTransition((ViewGroup) getView().findViewById(R.id.layout_transaction_details_layout_info));
		}
	}

	public static class TransactionExplorerFragment
			extends TransactionListFragment
			implements TransactionListAdapter.PathChangedListener, TitleSupport, SnackbarSupport
	{
		public static final String ARG_GROUP_ID = "argGroupId";
		public static final String ARG_PATH = "path";

		private RecyclerView mPathView;
		private TransactionPathResolverRecyclerAdapter mPathAdapter;

		@Override
		protected RecyclerView onListView(View mainContainer, ViewGroup listViewContainer)
		{
			View adaptedView = getLayoutInflater().inflate(R.layout.layout_transaction_explorer, null, false);
			listViewContainer.addView(adaptedView);

			mPathView = adaptedView.findViewById(R.id.layout_transaction_explorer_recycler);
			mPathAdapter = new TransactionPathResolverRecyclerAdapter(getContext());

			getAdapter().setPathChangedListener(this);

			LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
			layoutManager.setStackFromEnd(true);

			mPathView.setHasFixedSize(true);
			mPathView.setLayoutManager(layoutManager);
			mPathView.setAdapter(mPathAdapter);

			mPathAdapter.setOnClickListener(new PathResolverRecyclerAdapter.OnClickListener<String>()
			{
				@Override
				public void onClick(PathResolverRecyclerAdapter.Holder<String> holder)
				{
					goPath(getAdapter().getGroupId(), holder.index.object);
				}
			});

			return super.onListView(mainContainer, (FrameLayout) adaptedView.findViewById(R.id.layout_transaction_explorer_fragment_content));
		}

		@Override
		public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
		{
			super.onViewCreated(view, savedInstanceState);

			Bundle args = getArguments();

			if (args != null && args.containsKey(ARG_GROUP_ID))
				goPath(args.getLong(ARG_GROUP_ID), args.getString(ARG_PATH));
			else
				mPathAdapter.goTo(null);

			setMenuVisibility(isMenuShown());
		}

		@Override
		public void onPathChange(String path)
		{
			mPathAdapter.goTo(path == null ? null : path.split(File.separator));
			mPathAdapter.notifyDataSetChanged();

			if (mPathAdapter.getItemCount() > 0)
				mPathView.smoothScrollToPosition(mPathAdapter.getItemCount() - 1);
		}

		@Override
		public CharSequence getTitle(Context context)
		{
			return context.getString(R.string.text_files);
		}

		public void goPath(long groupId, String path)
		{
			getAdapter().setGroupId(groupId);
			getAdapter().setPath(path);

			refreshList();
		}
	}

	public static class CrunchLatestDataTask extends AsyncTask<TransactionActivity, Void, Void>
	{
		private PostExecuteListener mListener;

		public CrunchLatestDataTask(PostExecuteListener listener)
		{
			mListener = listener;
		}

		/* "possibility of having more than one TransactionActivity" < "sun turning into black hole" */
		@Override
		protected Void doInBackground(TransactionActivity... activities)
		{
			for (TransactionActivity activity : activities) {
				if (activity.getGroup() != null)
					activity.getDatabase()
							.calculateTransactionSize(activity.getGroup().groupId, activity.getIndex());
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid)
		{
			super.onPostExecute(aVoid);
			mListener.onPostExecute();
		}

		/* Should we have used a generic type class for this?
		 * This interface aims to keep its parent class non-anonymous
		 */

		public interface PostExecuteListener
		{
			void onPostExecute();
		}
	}
}
