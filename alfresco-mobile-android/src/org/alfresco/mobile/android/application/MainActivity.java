/*******************************************************************************
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 * 
 * This file is part of the Alfresco Mobile SDK.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package org.alfresco.mobile.android.application;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import org.alfresco.mobile.android.api.asynchronous.SessionLoader;
import org.alfresco.mobile.android.api.model.Document;
import org.alfresco.mobile.android.api.model.Folder;
import org.alfresco.mobile.android.api.model.Node;
import org.alfresco.mobile.android.api.model.Site;
import org.alfresco.mobile.android.api.session.AlfrescoSession;
import org.alfresco.mobile.android.application.accounts.Account;
import org.alfresco.mobile.android.application.accounts.fragment.AccountDetailsFragment;
import org.alfresco.mobile.android.application.accounts.fragment.AccountFragment;
import org.alfresco.mobile.android.application.accounts.fragment.AccountsLoader;
import org.alfresco.mobile.android.application.accounts.fragment.CreateAccountDialogFragment;
import org.alfresco.mobile.android.application.accounts.fragment.SignupCloudDialogFragment;
import org.alfresco.mobile.android.application.fragments.DisplayUtils;
import org.alfresco.mobile.android.application.fragments.FragmentDisplayer;
import org.alfresco.mobile.android.application.fragments.KeywordSearch;
import org.alfresco.mobile.android.application.fragments.about.AboutFragment;
import org.alfresco.mobile.android.application.fragments.activities.ActivitiesFragment;
import org.alfresco.mobile.android.application.fragments.browser.ChildrenBrowserFragment;
import org.alfresco.mobile.android.application.fragments.browser.local.LocalFileBrowserFragment;
import org.alfresco.mobile.android.application.fragments.comments.CommentsFragment;
import org.alfresco.mobile.android.application.fragments.menu.MainMenuFragment;
import org.alfresco.mobile.android.application.fragments.properties.DetailsFragment;
import org.alfresco.mobile.android.application.fragments.properties.ExtraDetailsFragment;
import org.alfresco.mobile.android.application.fragments.sites.BrowserSitesFragment;
import org.alfresco.mobile.android.application.fragments.versions.VersionFragment;
import org.alfresco.mobile.android.application.intent.IntentIntegrator;
import org.alfresco.mobile.android.application.loaders.NodeLoader;
import org.alfresco.mobile.android.application.loaders.NodeLoaderCallback;
import org.alfresco.mobile.android.application.manager.ActionManager;
import org.alfresco.mobile.android.application.utils.ConnectivityUtils;
import org.alfresco.mobile.android.application.utils.SessionUtils;
import org.alfresco.mobile.android.ui.fragments.BaseFragment;
import org.alfresco.mobile.android.ui.manager.MessengerManager;
import org.alfresco.mobile.android.ui.manager.StorageManager;
import org.alfresco.mobile.android.ui.properties.PropertiesFragment;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

public class MainActivity extends Activity implements LoaderCallbacks<List<Account>>, OnMenuItemClickListener
{

    private static final String TAG = "MainActivity";

    private Stack<String> stackCentral = new Stack<String>();

    private Node currentNode;

    private Map<Integer, Account> accounts;

    private boolean canExit = false;

    private int fragmentQueue = -1;

    private Account currentAccount;

    // ///////////////////////////////////////////
    // INIT
    // ///////////////////////////////////////////

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Loading progress
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.sdk_main);

        // Load Accounts
        getLoaderManager().restartLoader(AccountsLoader.ID, null, this);
        getLoaderManager().getLoader(AccountsLoader.ID).forceLoad();

        if (savedInstanceState != null)
        {
            currentAccount = (Account) savedInstanceState.getSerializable("account");

            String[] d = savedInstanceState.getStringArray("stackCentral");
            if (d != null)
            {
                List<String> list = Arrays.asList(d);
                stackCentral = new Stack<String>();
                stackCentral.addAll(list);
            }
        }
        else
        {
            clearScreen();
            if (IntentIntegrator.ACTION_CHECK_SIGNUP.equals(getIntent().getAction()))
                displayAccounts();
            else
                displayMainMenu();
        }

        initActionBar();
    }

    // ///////////////////////////////////////////
    // LOADER ACCOUNT
    // ///////////////////////////////////////////
    @Override
    public Loader<List<Account>> onCreateLoader(int id, Bundle args)
    {
        return new AccountsLoader(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        finish();
    }

    @Override
    // TODO Get without password
    public void onLoadFinished(Loader<List<Account>> arg0, List<Account> results)
    {

        if (results == null || results.isEmpty())
        {
            startActivityForResult(new Intent(this, HomeScreenActivity.class), 1);
            return;
        }

        // VIEW INTENT
        if (Intent.ACTION_VIEW.equals(getIntent().getAction())
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())
                || Intent.ACTION_SEND.equals(getIntent().getAction()))
        {

            String url = getIntent().getDataString();
            if (Intent.ACTION_SEND.equals(getIntent().getAction()) && getIntent().getExtras() != null
                    && getIntent().getExtras().getString(Intent.EXTRA_TEXT) != null)
            {
                url = getIntent().getExtras().getString(Intent.EXTRA_TEXT);
            }

            MessengerManager.showLongToast(this, url);
            // Load First Account by default
            NodeLoaderCallback call = new NodeLoaderCallback(this, results, url);
            LoaderManager lm = getLoaderManager();
            lm.restartLoader(NodeLoader.ID, null, call);
            lm.getLoader(NodeLoader.ID).forceLoad();
            return;
        }

        accounts = new HashMap<Integer, Account>(results.size());
        for (Account account : results)
        {
            accounts.put((int) account.getId(), account);
        }

        if (getSession() == null)
        {
            setProgressBarIndeterminateVisibility(true);
            currentAccount = results.get(0);
            if (currentAccount.getActivation() == null && hasNetwork())
            {
                // Load First Account by default
                LoginLoaderCallback call = new LoginLoaderCallback(this, currentAccount.getUrl(),
                        currentAccount.getUsername(), currentAccount.getPassword());
                LoaderManager lm = getLoaderManager();
                lm.restartLoader(SessionLoader.ID, null, call);
                lm.getLoader(SessionLoader.ID).forceLoad();
            }
        }
        else
        {
            currentAccount = SessionUtils.getAccount(this);
            if (currentAccount == null) currentAccount = results.get(0);
        }
        SessionUtils.setAccount(this, currentAccount);
        createSwitchAccount(currentAccount);
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        if (currentAccount != null)
            createSwitchAccount(currentAccount);
        else if (accounts != null)
        {
            currentAccount = accounts.get(0);
            createSwitchAccount(currentAccount);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Account>> arg0)
    {
        // TODO Auto-generated method stub
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        Boolean backstack = false;

        // Intent after session loading
        if (IntentIntegrator.ACTION_LOAD_SESSION_FINISH.equals(intent.getAction()))
        {
            if (fragmentQueue != -1) doMainMenuAction(fragmentQueue);
            fragmentQueue = -1;
            setProgressBarIndeterminateVisibility(false);
        }
        else if (Intent.ACTION_VIEW.equals(intent.getAction()) && IntentIntegrator.NODE_TYPE.equals(intent.getType()))
        {
            if (intent.getExtras().containsKey(IntentIntegrator.EXTRA_NODE))
            {
                BaseFragment frag = DetailsFragment.newInstance((Document) intent.getExtras().get(
                        IntentIntegrator.EXTRA_NODE));
                frag.setSession(SessionUtils.getsession(this));
                FragmentDisplayer.replaceFragment(this, frag, getFragmentPlace(), DetailsFragment.TAG, false);
            }
            else
            {

            }
        }
        else if (IntentIntegrator.ACTION_CHECK_SIGNUP.equals(intent.getAction()))
        {
            FragmentDisplayer.removeFragment(this, SignupCloudDialogFragment.TAG);
            displayAccounts();
        }
        else if (IntentIntegrator.ACTION_DISPLAY_NODE.equals(intent.getAction()))
        {
            // case phone
            if (!DisplayUtils.hasCentralPane(this) && getFragment(DetailsFragment.TAG) != null) return;

            if (SessionUtils.getAccount(this) != null) currentAccount = SessionUtils.getAccount(this);
            createSwitchAccount(currentAccount);
            if (currentNode.isDocument())
                addPropertiesFragment(currentNode);
            else
                addNavigationFragment((Folder) currentNode);
        }
        else if (Intent.ACTION_VIEW.equals(intent.getAction())
                && "org.alfresco.mobile.android".equals(intent.getData().getScheme()))
        {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            Fragment prev = getFragmentManager().findFragmentByTag(SignupCloudDialogFragment.TAG);
            if (prev != null) ft.remove(prev);
            prev = getFragmentManager().findFragmentByTag(CreateAccountDialogFragment.TAG);
            if (prev != null) ft.remove(prev);

            // Create and show the dialog.
            SignupCloudDialogFragment newFragment = new SignupCloudDialogFragment();
            newFragment.show(ft, SignupCloudDialogFragment.TAG);
        }
        else if (IntentIntegrator.ACTION_REFRESH.equals(intent.getAction()))
        {
            if (intent.getCategories().contains(IntentIntegrator.CATEGORY_REFRESH_OTHERS))
            {
                if (IntentIntegrator.ACCOUNT_TYPE.equals(intent.getType()))
                {
                    if (((AccountFragment) getFragment(AccountFragment.TAG)) != null)
                    {
                        ((AccountFragment) getFragment(AccountFragment.TAG)).refresh();
                        FragmentDisplayer.removeFragment(this, AccountDetailsFragment.TAG);
                    }
                    if (!DisplayUtils.hasCentralPane(this)) getFragmentManager().popBackStack();
                    getLoaderManager().restartLoader(AccountsLoader.ID, null, this);
                    getLoaderManager().getLoader(AccountsLoader.ID).forceLoad();
                }
                else if (IntentIntegrator.FILE_TYPE.equals(intent.getType()))
                {
                    ((LocalFileBrowserFragment) getFragment(LocalFileBrowserFragment.TAG)).refresh();
                }
                else
                {
                    ((ChildrenBrowserFragment) getFragment(ChildrenBrowserFragment.TAG)).refresh();
                    FragmentDisplayer.removeFragment(this, DetailsFragment.TAG);
                    if (!DisplayUtils.hasCentralPane(this)) getFragmentManager().popBackStack();
                }
            }
            else if (intent.getCategories().contains(IntentIntegrator.CATEGORY_REFRESH_ALL))
            {
                if (getFragment(ChildrenBrowserFragment.TAG) != null)
                    ((ChildrenBrowserFragment) getFragment(ChildrenBrowserFragment.TAG)).refresh();
                FragmentDisplayer.removeFragment(this, DetailsFragment.TAG);
                if (!DisplayUtils.hasCentralPane(this))
                {
                    backstack = true;
                    getFragmentManager().popBackStack();
                }
                addPropertiesFragment(currentNode, backstack);
            }
            else if (intent.getCategories().contains(IntentIntegrator.CATEGORY_REFRESH_DELETE))
            {
                ((ChildrenBrowserFragment) getFragment(ChildrenBrowserFragment.TAG)).refresh();
                if (intent.getExtras() != null && intent.getExtras().getBundle(ActionManager.REFRESH_EXTRA) != null)
                {
                    if (currentNode != null
                            && !currentNode.getIdentifier().equals(
                                    intent.getExtras().getBundle(ActionManager.REFRESH_EXTRA)
                                            .getString(IntentIntegrator.EXTRA_NODE)))
                    {
                        FragmentDisplayer.removeFragment(this, DetailsFragment.TAG);
                        if (!DisplayUtils.hasCentralPane(this))
                        {
                            backstack = true;
                            getFragmentManager().popBackStack();
                        }
                        addPropertiesFragment(currentNode, backstack);
                    }
                }
                else
                {
                    FragmentDisplayer.removeFragment(this, DetailsFragment.TAG);
                    if (!DisplayUtils.hasCentralPane(this))
                    {
                        backstack = true;
                        getFragmentManager().popBackStack();
                    }
                    addPropertiesFragment(currentNode, backstack);
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        String[] stringArray = Arrays.copyOf(stackCentral.toArray(), stackCentral.size(), String[].class);
        outState.putStringArray("stackCentral", stringArray);
        outState.putSerializable("account", currentAccount);
    }

    // ///////////////////////////////////////////
    // SWITCH ACCOUNT
    // ///////////////////////////////////////////
    public void createSwitchAccount(Account account)
    {
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View v = layoutInflater.inflate(R.layout.sdkapp_list_quickaccount, null);
        ((TextView) v.findViewById(R.id.toptext)).setText(account.getDescription() + "  ");
        ((TextView) v.findViewById(R.id.bottomtext)).setText(account.getUsername() + "  ");

        v.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                showPopup(v);
            }
        });
        getActionBar().setCustomView(v);
        getActionBar().show();
    }

    public void showPopup(View v)
    {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(this);
        if (accounts != null && !accounts.isEmpty())
        {
            for (Entry<Integer, Account> account : accounts.entrySet())
            {
                popup.getMenu().add(Menu.NONE, (int) account.getKey(), Menu.NONE,
                        account.getValue().getDescription() + " (" + account.getValue().getUsername() + ")");
            }
        }
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item)
    {
        Account acc = accounts.get(item.getItemId());

        if (getSession() != null
                && (!acc.getUrl().equals(getSession().getBaseUrl()) || !acc.getUsername().equals(
                        getSession().getPersonIdentifier())))
        {
            setProgressBarIndeterminateVisibility(true);
            currentAccount = acc;
            SessionUtils.setsession(this, null);
            LoginLoaderCallback call = new LoginLoaderCallback(MainActivity.this, acc.getUrl(), acc.getUsername(),
                    acc.getPassword());
            LoaderManager lm = getLoaderManager();
            lm.restartLoader(SessionLoader.ID, null, call);
            lm.getLoader(SessionLoader.ID).forceLoad();
            clearScreen();
            getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            displayMainMenu();
            createSwitchAccount(acc);
        }

        return false;
    }

    // ///////////////////////////////////////////
    // SLIDE MENU
    // ///////////////////////////////////////////
    private void toggleSlideMenu()
    {
        if (getFragment(MainMenuFragment.TAG) != null && getFragment(MainMenuFragment.TAG).isAdded()) return;
        View slideMenu = findViewById(R.id.slide_pane);
        if (slideMenu.getVisibility() == View.VISIBLE)
        {
            hideSlideMenu();
        }
        else
        {
            showSlideMenu();
        }
    }

    private void hideSlideMenu()
    {
        View slideMenu = findViewById(R.id.slide_pane);
        slideMenu.setVisibility(View.GONE);
        slideMenu.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rbm_out_to_left));
    }
    
    private boolean isSlideMenuVisible(){
        return findViewById(R.id.slide_pane).getVisibility() == View.VISIBLE;
    }

    private void showSlideMenu()
    {
        View slideMenu = findViewById(R.id.slide_pane);
        slideMenu.setVisibility(View.VISIBLE);
        slideMenu.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rbm_in_from_left));
    }

    private void doMainMenuAction(int id)
    {
        BaseFragment frag = null;
        switch (id)
        {
            case R.id.menu_browse_my_sites:
                if (!checkSession(R.id.menu_browse_favorite_sites)) return;
                frag = BrowserSitesFragment.newInstance();
                FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                        BrowserSitesFragment.TAG, true);
                break;
            case R.id.menu_browse_root:
                if (!checkSession(R.id.menu_browse_root)) return;
                frag = ChildrenBrowserFragment.newInstance(getSession().getRootFolder());
                frag.setSession(SessionUtils.getsession(this));
                FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                        ChildrenBrowserFragment.TAG, true);
                break;
            case R.id.menu_account_manage:
                FragmentDisplayer
                        .replaceFragment(this, DisplayUtils.getLeftFragmentId(this), AccountFragment.TAG, true);
                break;
            case R.id.menu_browse_activities:
                if (!checkSession(R.id.menu_browse_activities)) return;
                frag = ActivitiesFragment.newInstance();
                FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                        ActivitiesFragment.TAG, true);
                break;
            case R.id.menu_search:
                if (!checkSession(R.id.menu_search)) return;
                FragmentDisplayer.replaceFragment(this, DisplayUtils.getLeftFragmentId(this), KeywordSearch.TAG, true);
                break;
            case R.id.menu_download:
                addLocalFileNavigationFragment(StorageManager.getDownloadFolder(this, SessionUtils.getsession(this).getBaseUrl(), SessionUtils.getsession(this).getPersonIdentifier()));
                //FragmentDisplayer.replaceFragment(this, DisplayUtils.getLeftFragmentId(this),
                //        addLocalFileNavigationFragment.TAG, true);
                break;
            case R.id.menu_about:
                showAbout();
                break;
            default:
                break;
        }
    }

    public void showMainMenuFragment(View v)
    {
        canExit = false;
        clearScreen();
        DisplayUtils.hideLeftTitlePane(this);
        doMainMenuAction(v.getId());
        toggleSlideMenu();
    }

    private boolean checkSession(int actionMainMenuId)
    {
        if (!hasNetwork())
            return false;
        else if (SessionUtils.getAccount(this) != null && SessionUtils.getAccount(this).getActivation() != null)
        {
            MessengerManager.showToast(this, "Your account is not activated. Please check manage account screen.");
            fragmentQueue = actionMainMenuId;
            return false;
        }
        else if (SessionUtils.getsession(this) == null)
        {
            MessengerManager.showToast(this, "Session is loading... Automatic refresh when connecting...");
            fragmentQueue = actionMainMenuId;
            return false;
        }

        return true;
    }

    // ///////////////////////////////////////////
    // FRAGMENTS
    // ///////////////////////////////////////////
    public void addNavigationFragment(Folder f)
    {
        clearScreen();
        clearCentralPane();
        BaseFragment frag = ChildrenBrowserFragment.newInstance(f);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                ChildrenBrowserFragment.TAG, true);
    }

    public void addNavigationFragment(String path)
    {
        clearScreen();
        clearCentralPane();
        BaseFragment frag = ChildrenBrowserFragment.newInstance(path);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                ChildrenBrowserFragment.TAG, true);
    }

    public void addNavigationFragment(Site s)
    {
        clearScreen();
        clearCentralPane();
        BaseFragment frag = ChildrenBrowserFragment.newInstance(s);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                ChildrenBrowserFragment.TAG, true);
    }

    public void addLocalFileNavigationFragment(File file)
    {
        clearCentralPane();
        BaseFragment frag = LocalFileBrowserFragment.newInstance(file);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getLeftFragmentId(this),
                LocalFileBrowserFragment.TAG, true);
    }

    public void addPropertiesFragment(Node n, boolean forceBackStack)
    {
        if (DisplayUtils.hasCentralPane(this))
        {
            stackCentral.clear();
            stackCentral.push(DetailsFragment.TAG);
        }
        BaseFragment frag = DetailsFragment.newInstance(n);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, getFragmentPlace(), DetailsFragment.TAG, forceBackStack);
        DisplayUtils.getMainPane(this).setBackgroundResource(android.R.color.background_light);
    }

    public void addPropertiesFragment(Node n)
    {
        Boolean b = DisplayUtils.hasCentralPane(this) ? false : true;
        addPropertiesFragment(n, b);
    }

    public void addComments(Node n)
    {
        if (DisplayUtils.hasCentralPane(this)) stackCentral.push(CommentsFragment.TAG);
        BaseFragment frag = CommentsFragment.newInstance(n);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, getFragmentPlace(true), CommentsFragment.TAG, true);
        ((View) findViewById(getFragmentPlace(true)).getParent()).setVisibility(View.VISIBLE);
    }

    public void addExtraDetails(Node n)
    {
        if (DisplayUtils.hasCentralPane(this)) stackCentral.push(ExtraDetailsFragment.TAG);
        BaseFragment frag = ExtraDetailsFragment.newInstance(n);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, R.id.right_pane_body, ExtraDetailsFragment.TAG, false);
        ((View) findViewById(R.id.right_pane_body).getParent()).setVisibility(View.VISIBLE);
    }

    public void addVersions(Document d)
    {
        if (DisplayUtils.hasCentralPane(this)) stackCentral.push(VersionFragment.TAG);
        BaseFragment frag = VersionFragment.newInstance(d);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, getFragmentPlace(true), VersionFragment.TAG, true);
        ((View) findViewById(getFragmentPlace(true)).getParent()).setVisibility(View.VISIBLE);
    }

    public void addAccountDetails(long id)
    {
        BaseFragment frag = AccountDetailsFragment.newInstance(id);
        frag.setSession(SessionUtils.getsession(this));
        FragmentDisplayer.replaceFragment(this, frag, DisplayUtils.getMainPaneId(this), AccountDetailsFragment.TAG,
                true);
        DisplayUtils.getMainPane(this).setBackgroundResource(android.R.color.background_light);
    }

    public void showAbout()
    {
        Fragment f = new AboutFragment();
        FragmentDisplayer.replaceFragment(this, f, DisplayUtils.getMainPaneId(this), AboutFragment.TAG, true);
    }

    public void displayMainMenu()
    {
        Fragment f = new MainMenuFragment();
        FragmentDisplayer.replaceFragment(this, f, DisplayUtils.getLeftFragmentId(this), MainMenuFragment.TAG, false);
        hideSlideMenu();
    }

    public void displayAccounts()
    {
        Fragment f = new AccountFragment();
        FragmentDisplayer.replaceFragment(this, f, DisplayUtils.getLeftFragmentId(this), AccountFragment.TAG, true);
    }

    // ///////////////////////////////////////////
    // UTILS FRAGMENTS
    // ///////////////////////////////////////////
    public int getFragmentPlace()
    {
        int id = R.id.left_pane_body;
        if (DisplayUtils.hasCentralPane(this)) id = R.id.central_pane_body;
        return id;
    }

    public int getFragmentPlace(boolean right)
    {
        int id = R.id.left_pane_body;
        if (right && DisplayUtils.hasRightPane(this))
            id = R.id.right_pane_body;
        else if (DisplayUtils.hasCentralPane(this)) id = R.id.central_pane_body;
        return id;
    }

    public int getFragmentPlaceId()
    {
        if (DisplayUtils.hasRightPane(this))
        {
            return DisplayUtils.getRightFragmentId(this);
        }
        else if (DisplayUtils.hasCentralPane(this))
        {
            return DisplayUtils.getCentralFragmentId(this);
        }
        else
        {
            return DisplayUtils.getLeftFragmentId(this);
        }
    }

    public void clearScreen()
    {
        if (DisplayUtils.hasRightPane(this))
        {
            FragmentDisplayer.removeFragment(this, DisplayUtils.getRightFragmentId(this));
            DisplayUtils.hide(DisplayUtils.getRightPane(this));
        }
        if (DisplayUtils.hasCentralPane(this))
        {
            FragmentDisplayer.removeFragment(this, DisplayUtils.getCentralFragmentId(this));
            DisplayUtils.getCentralPane(this).setBackgroundResource(R.drawable.background_grey_alfresco);
        }
        if (DisplayUtils.hasLeftPane(this))
        {
            DisplayUtils.show(DisplayUtils.getLeftPane(this));
            // FragmentDisplayer.removeFragment(this,
            // DisplayUtils.getLeftFragmentId(this));
            // DisplayUtils.getLeftPane(this).setBackgroundResource(android.R.color.transparent);
        }
    }

    public void clearCentralPane()
    {
        FragmentDisplayer.removeFragment(this, stackCentral);
        stackCentral.clear();
    }

    // //////////////////////////////////////////////////////////////////////
    // ///////// ACTION BAR ///////////////////
    // //////////////////////////////////////////////////////////////////////
    @TargetApi(14)
    private void initActionBar()
    {
        try
        {
            ActionBar bar = getActionBar();
            bar.setBackgroundDrawable(getResources().getDrawable(R.drawable.black_bar));

            // Create Quick Account Icon.
            LinearLayout l1 = new LinearLayout(this);
            l1.setOrientation(LinearLayout.VERTICAL);
            int value = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) 50, getResources()
                    .getDisplayMetrics());
            l1.setLayoutParams(new LayoutParams(value, LayoutParams.MATCH_PARENT));
            l1.setGravity(Gravity.CENTER);
            l1.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    showPopup(v);
                }
            });

            ImageView im = new ImageView(this);
            im.setBackgroundDrawable(getResources().getDrawable(R.drawable.switch_account));
            im.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            l1.addView(im);
            getActionBar().setCustomView(l1);

            bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME);

            if (DisplayUtils.hasCentralPane(this))
            {
                bar.setDisplayShowTitleEnabled(false);
                bar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO, ActionBar.DISPLAY_USE_LOGO);
            }
            else
            {
                bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_USE_LOGO);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            {
                bar.setHomeButtonEnabled(true);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        MenuItem mi;

        if (isVisible(AccountFragment.TAG)) ((AccountFragment) getFragment(AccountFragment.TAG)).getMenu(menu);

        if (isVisible(AccountDetailsFragment.TAG))
            ((AccountDetailsFragment) getFragment(AccountDetailsFragment.TAG)).getMenu(menu);

        else if (isVisible(DetailsFragment.TAG))
        {
            ((DetailsFragment) getFragment(DetailsFragment.TAG)).getMenu(menu);
        }
        else if (isVisible(KeywordSearch.TAG))
        {
            mi = menu.add(Menu.NONE, MenuActionItem.MENU_SEARCH_OPTION, Menu.FIRST + MenuActionItem.MENU_SEARCH_OPTION,
                    R.string.search_option);
            mi.setIcon(R.drawable.ic_menu_find_holo_dark);
            mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        else if (isVisible(ChildrenBrowserFragment.TAG))
        {
            ((ChildrenBrowserFragment) getFragment(ChildrenBrowserFragment.TAG)).getMenu(menu);
        }

        return true;
    }

    private boolean isVisible(String tag)
    {
        return getFragmentManager().findFragmentByTag(tag) != null
                && getFragmentManager().findFragmentByTag(tag).isAdded();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case MenuActionItem.MENU_ACCOUNT_ADD:
                ((AccountFragment) getFragment(AccountFragment.TAG)).add();
                return true;

            case MenuActionItem.MENU_ACCOUNT_EDIT:
                ((AccountDetailsFragment) getFragment(AccountDetailsFragment.TAG)).edit();
                return true;

            case MenuActionItem.MENU_ACCOUNT_DELETE:
                ((AccountDetailsFragment) getFragment(AccountDetailsFragment.TAG)).delete();
                return true;

            case MenuActionItem.MENU_SEARCH:
                FragmentDisplayer.replaceFragment(this, new KeywordSearch(), getFragmentPlace(), KeywordSearch.TAG,
                        true);
                return true;
            case MenuActionItem.MENU_CREATE_FOLDER:
                ((ChildrenBrowserFragment) getFragment(ChildrenBrowserFragment.TAG)).createFolder();
                return true;
            case MenuActionItem.MENU_UPLOAD:
                ActionManager.actionPickFile(getFragment(ChildrenBrowserFragment.TAG),
                        IntentIntegrator.REQUESTCODE_FILEPICKER);
                return true;
            case MenuActionItem.MENU_DELETE_FOLDER:
                // ((DetailsFragment)
                // getFragment(DetailsFragment.TAG)).delete();
                return true;
            case MenuActionItem.MENU_REFRESH:
                ((ChildrenBrowserFragment) getFragment(ChildrenBrowserFragment.TAG)).refresh();
                return true;

            case MenuActionItem.MENU_SHARE:
                ((DetailsFragment) getFragment(DetailsFragment.TAG)).share();
                return true;
            case MenuActionItem.MENU_OPEN_IN:
                ((DetailsFragment) getFragment(DetailsFragment.TAG)).openin();
                return true;
            case MenuActionItem.MENU_DOWNLOAD:
                ((DetailsFragment) getFragment(DetailsFragment.TAG)).download();
                return true;
            case MenuActionItem.MENU_UPDATE:
                ((DetailsFragment) getFragment(DetailsFragment.TAG)).update();
                return true;
            case MenuActionItem.MENU_EDIT:
                ((DetailsFragment) getFragment(DetailsFragment.TAG)).edit();
                return true;
            case MenuActionItem.MENU_DELETE:
                ((DetailsFragment) getFragment(DetailsFragment.TAG)).delete();
                return true;

            case MenuActionItem.MENU_SEARCH_OPTION:
                MessengerManager.showToast(this, "Display Search Parameters like isExact...");
                return true;

            case MenuActionItem.ACCOUNT_ID:
                // getActionBar().addTab(getActionBar().newTab().setText("Accounts").setTag(AccountFragment.TAG).setTabListener(new
                // TabListener<AccountFragment>(this, AccountFragment.TAG)));
                // FragmentDisplayer.replaceFragment(this, R.id.left_pane_body,
                // AccountFragment.TAG, false);
                // clearCentralPane();
                return true;
            case MenuActionItem.PARAMETER_ID:
                MessengerManager.showToast(this, "Parameter");
                return true;
            case MenuActionItem.ABOUT_ID:
                showAbout();
                return true;

            case android.R.id.home:
                // app icon in action bar clicked; go home
                /*
                 * Intent intent = new Intent(this, HomeScreenActivity.class);
                 * intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                 * startActivity(intent);
                 */
                toggleSlideMenu();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed()
    {
        if (isSlideMenuVisible())
            hideSlideMenu();
        else
            super.onBackPressed();

        if (DisplayUtils.hasCentralPane(this)) invalidateOptionsMenu();
    }

    // ///////////////////////////////////////////
    // UTILS
    // ///////////////////////////////////////////

    private Fragment getFragment(String tag)
    {
        return getFragmentManager().findFragmentByTag(tag);
    }

    public AlfrescoSession getSession()
    {
        return SessionUtils.getsession(this);
    }

    public Account getAccount()
    {
        return currentAccount;
    }

    public Node getCurrentNode()
    {
        return currentNode;
    }

    public void setCurrentNode(Node currentNode)
    {
        this.currentNode = currentNode;
    }

    public void refreshAccount()
    {
        getLoaderManager().restartLoader(AccountsLoader.ID, null, this);
        getLoaderManager().getLoader(AccountsLoader.ID).forceLoad();
    }

    private boolean hasNetwork()
    {
        if (!ConnectivityUtils.hasInternetAvailable(this))
        {
            showDialog(NETWORK_PROBLEM);
            return false;
        }
        else
        {
            return true;
        }
    }

    public static final int CLOUD_RESEND_EMAIL = 500;

    public static final int NETWORK_PROBLEM = 600;

    @Override
    protected Dialog onCreateDialog(int id)
    {
        AlertDialog dialog = null;
        switch (id)
        {
            case CLOUD_RESEND_EMAIL:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.cloud_signup_resend_successfull)
                        .setMessage(R.string.cloud_signup_resend_body).setCancelable(false)
                        .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                dialog.dismiss();
                            }
                        }).create();
                dialog.show();
                break;
            case NETWORK_PROBLEM:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.error_network_title)
                        .setMessage(R.string.error_network_details).setCancelable(false)
                        .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                dialog.dismiss();
                            }
                        }).create();
                dialog.show();
        }
        // TODO Auto-generated method stub
        return super.onCreateDialog(id);
    }
}