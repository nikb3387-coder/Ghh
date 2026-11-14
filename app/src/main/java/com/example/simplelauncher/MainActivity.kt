package com.example.simplelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var startPanel: View
    private lateinit var search: EditText
    private lateinit var tilesList: RecyclerView
    private lateinit var allList: RecyclerView
    private lateinit var emptyTiles: TextView
    private lateinit var tabTiles: TextView
    private lateinit var tabAll: TextView
    private lateinit var taskbarIcons: LinearLayout
    private lateinit var pcWindow: View
    private lateinit var tilesAdapter: TilesAdapter
    private lateinit var allAdapter: AllAppsAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var showingAll = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        startPanel = findViewById(R.id.startPanel)
        search = findViewById(R.id.search)
        tilesList = findViewById(R.id.tilesList)
        allList = findViewById(R.id.allList)
        emptyTiles = findViewById(R.id.emptyTiles)
        tabTiles = findViewById(R.id.tabTiles)
        tabAll = findViewById(R.id.tabAll)
        taskbarIcons = findViewById(R.id.taskbarIcons)
        pcWindow = findViewById(R.id.pcWindow)

        val lp = startPanel.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = (resources.displayMetrics.heightPixels * 0.25f).toInt()
        startPanel.layoutParams = lp

        tilesAdapter = TilesAdapter(::launchApp, ::showMenu)
        allAdapter = AllAppsAdapter(::launchApp, ::showMenu)
        tilesList.layoutManager = GridLayoutManager(this, 3)
        tilesList.adapter = tilesAdapter
        allList.layoutManager = LinearLayoutManager(this)
        allList.adapter = allAdapter

        findViewById<View>(R.id.desktop).setOnClickListener { hidePanel() }
        findViewById<View>(R.id.pcIcon).setOnClickListener { showPcWindow() }
        findViewById<View>(R.id.pcClose).setOnClickListener { pcWindow.visibility = View.GONE }
        findViewById<View>(R.id.driveItem).setOnClickListener { openFiles() }
        findViewById<View>(R.id.startButton).setOnClickListener {
            if (startPanel.visibility == View.VISIBLE) hidePanel() else showPanel(false, false)
        }
        findViewById<View>(R.id.taskbarSearch).setOnClickListener { showPanel(true, true) }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            hidePanel()
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        tabTiles.setOnClickListener {
            search.setText("")
            selectTab(false)
        }
        tabAll.setOnClickListener { selectTab(true) }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty() && !showingAll) selectTab(true)
                refreshAll()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filteredApps().firstOrNull()?.let { launchApp(it) }
                true
            } else false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (startPanel.visibility == View.VISIBLE) hidePanel()
                else if (pcWindow.visibility == View.VISIBLE) pcWindow.visibility = View.GONE
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)

        selectTab(false)
        loadApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) hidePanel()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    AppInfo(
                        label = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        icon = it.loadIcon(pm)
                    )
                }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
            runOnUiThread {
                allApps = list
                refreshAll()
                refreshTiles()
                refreshTaskbar()
            }
        }.start()
    }

    private fun showPanel(all: Boolean, focusSearch: Boolean) {
        startPanel.visibility = View.VISIBLE
        selectTab(all)
        if (focusSearch) {
            search.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hidePanel() {
        search.setText("")
        search.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        startPanel.visibility = View.GONE
        selectTab(false)
    }

    private fun selectTab(all: Boolean) {
        showingAll = all
        tilesList.visibility = if (all) View.GONE else View.VISIBLE
        allList.visibility = if (all) View.VISIBLE else View.GONE
        tabTiles.setBackgroundResource(if (all) 0 else R.drawable.tab_active)
        tabAll.setBackgroundResource(if (all) R.drawable.tab_active else 0)
        tabTiles.setTextColor(if (all) 0xFFAAAAAA.toInt() else 0xFFFFFFFF.toInt())
        tabAll.setTextColor(if (all) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt())
        updateEmptyTiles()
    }

    private fun filteredApps(): List<AppInfo> {
        val q = search.text.toString().trim().lowercase(Locale.getDefault())
        return if (q.isEmpty()) allApps
        else allApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
    }

    private fun refreshAll() {
        val searching = search.text.toString().trim().isNotEmpty()
        val rows = mutableListOf<Row>()
        var last = ""
        for (app in filteredApps()) {
            if (!searching) {
                val l = letterOf(app.label)
                if (l != last) {
                    rows.add(Row.Header(l))
                    last = l
                }
            }
            rows.add(Row.App(app))
        }
        allAdapter.submit(rows)
    }

    private fun letterOf(label: String): String {
        val c = label.trim().firstOrNull()?.uppercaseChar()
        return if (c != null && c.isLetter()) c.toString() else "#"
    }

    private fun pinnedApps(key: String): List<AppInfo> =
        prefs.get(key).mapNotNull { pkg -> allApps.find { it.packageName == pkg } }

    private fun refreshTiles() {
        tilesAdapter.submit(pinnedApps(Prefs.TILES))
        updateEmptyTiles()
    }

    private fun updateEmptyTiles() {
        val empty = pinnedApps(Prefs.TILES).isEmpty()
        emptyTiles.visibility = if (!showingAll && empty) View.VISIBLE else View.GONE
    }

    private fun refreshTaskbar() {
        taskbarIcons.removeAllViews()
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        for (app in pinnedApps(Prefs.TASKBAR)) {
            val iv = ImageView(this)
            iv.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            iv.setPadding(dp(8), dp(8), dp(8), dp(8))
            iv.setImageDrawable(app.icon)
            iv.setBackgroundResource(tv.resourceId)
            iv.contentDescription = app.label
            iv.setOnClickListener { launchApp(app) }
            iv.setOnLongClickListener {
                showMenu(it, app)
                true
            }
            taskbarIcons.addView(iv)
        }
    }

    private fun showPcWindow() {
        hidePanel()
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBytesLong
        val pct = if (total > 0) ((total - free) * 100 / total).toInt() else 0
        val bar = findViewById<ProgressBar>(R.id.driveBar)
        bar.progress = pct
        bar.progressTintList = ColorStateList.valueOf(
            if (pct >= 90) 0xFFDA3B01.toInt() else 0xFF26A0DA.toInt()
        )
        findViewById<TextView>(R.id.driveInfo).text =
            getString(R.string.drive_info, gb(free), gb(total))
        pcWindow.visibility = View.VISIBLE
    }

    private fun gb(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f", bytes / 1073741824.0)

    private fun openFiles() {
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                "vnd.android.document/root"
            ),
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        )
        for (i in attempts) {
            try {
                startActivity(i)
                return
            } catch (e: Exception) {
                // пробуем следующий вариант
            }
        }
    }

    private fun launchApp(app: AppInfo) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            hidePanel()
            startActivity(it)
        }
    }

    private fun toggle(key: String, pkg: String, limit: Int = Int.MAX_VALUE): Boolean {
        val list = prefs.get(key).toMutableList()
        if (list.remove(pkg)) {
            prefs.set(key, list)
            return true
        }
        if (list.size >= limit) return false
        list.add(pkg)
        prefs.set(key, list)
        return true
    }

    private fun showMenu(anchor: View, app: AppInfo) {
        val pinnedTile = app.packageName in prefs.get(Prefs.TILES)
        val pinnedBar = app.packageName in prefs.get(Prefs.TASKBAR)
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (pinnedTile) R.string.unpin_start else R.string.pin_start)
            menu.add(0, 2, 1, if (pinnedBar) R.string.unpin_taskbar else R.string.pin_taskbar)
            menu.add(0, 3, 2, R.string.app_info)
            menu.add(0, 4, 3, R.string.uninstall)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        toggle(Prefs.TILES, app.packageName)
                        refreshTiles()
                    }
                    2 -> {
                        if (!toggle(Prefs.TASKBAR, app.packageName, 3)) {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.taskbar_full,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        refreshTaskbar()
                    }
                    3 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${app.packageName}"))
                    )
                    4 -> startActivity(
                        Intent(Intent.ACTION_DELETE)
                            .setData(Uri.parse("package:${app.packageName}"))
                    )
                }
                true
            }
            show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
