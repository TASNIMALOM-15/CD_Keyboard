package com.example.bkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class ClipboardActivity : Activity() {
    private lateinit var db: ClipDb
    private lateinit var search: EditText
    private lateinit var spin: Spinner
    private lateinit var listView: ListView
    private lateinit var bulkBtn: Button
    private var folders = listOf<Folder>()
    private var keys = listOf<Long?>()          // null = All, -1 = Favorites, 0 = Unfiled, else folder id
    private var curKey: Long? = null
    private var sort = 0
    private var items = listOf<Clip>()
    private val selected = HashSet<Long>()
    private val unlocked = HashSet<Long>()
    private var bulk = false
    private var pendingKey: Long = 0
    private var exportPass = ""
    private var importBytes: ByteArray? = null

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = items[p].id
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val c = items[p]
            val tv = (v as? TextView) ?: TextView(this@ClipboardActivity)
            tv.setPadding(dp(12), dp(10), dp(12), dp(10))
            tv.maxLines = 3
            tv.text = (if (c.pin) "📌 " else "") + (if (c.fav) "⭐ " else "") + c.text.take(200) +
                (if (c.tags.isNotBlank()) "\n#" + c.tags.replace(",", " #") else "")
            tv.setBackgroundColor(if (c.id in selected) Color.parseColor("#BBDEFB") else Color.TRANSPARENT)
            return tv
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Clipboard"
        db = ClipDb(this)
        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(8), dp(8), dp(8), dp(8))
        search = EditText(this); search.hint = "Search text / #tags"
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(search)
        val r1 = LinearLayout(this)
        spin = Spinner(this)
        r1.addView(spin, LinearLayout.LayoutParams(0, -2, 1f))
        val sortBtn = Button(this); sortBtn.text = "Sort"
        sortBtn.setOnClickListener {
            sort = (sort + 1) % 3
            toast(listOf("Newest first", "Oldest first", "A–Z")[sort]); refresh()
        }
        r1.addView(sortBtn)
        bulkBtn = Button(this); bulkBtn.text = "Bulk"
        bulkBtn.setOnClickListener { toggleBulk() }
        r1.addView(bulkBtn)
        root.addView(r1)
        val r2 = LinearLayout(this)
        fun small(t: String, a: () -> Unit) { val x = Button(this); x.text = t; x.setOnClickListener { a() }; r2.addView(x, LinearLayout.LayoutParams(0, -2, 1f)) }
        small("Export") { askExport() }
        small("Import") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 2) }
        small("+Folder") { newFolder() }
        small("Clean") { confirm("Delete ALL items that are not pinned or favorite?") { db.deleteUnmarked(); refresh() } }
        root.addView(r2)
        listView = ListView(this)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, p, _ -> onItem(items[p]) }
        root.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos >= keys.size) return
                val k = keys[pos]
                if (k == curKey) return
                if (k != null && k > 0 && folders.firstOrNull { it.id == k }?.locked == true && k !in unlocked) {
                    authenticate(k); return
                }
                curKey = k; refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refreshSpinner()
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun path(f: Folder, depth: Int = 0): String {
        if (f.parent == 0L || depth > 8) return f.name
        val p = folders.firstOrNull { it.id == f.parent } ?: return f.name
        return path(p, depth + 1) + " / " + f.name
    }

    private fun refreshSpinner() {
        folders = db.folders()
        val labels = ArrayList<String>(); val k = ArrayList<Long?>()
        labels += "All"; k += null
        labels += "★ Favorites"; k += -1L
        labels += "Unfiled"; k += 0L
        for (f in folders) { labels += (if (f.locked) "🔒 " else "📁 ") + path(f); k += f.id }
        keys = k
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val idx = keys.indexOf(curKey)
        spin.setSelection(if (idx >= 0) idx else 0)
    }

    private fun refresh() {
        items = when (curKey) {
            null -> db.list(search.text.toString(), null, sort)
            -1L -> db.list(search.text.toString(), null, sort, favOnly = true)
            else -> db.list(search.text.toString(), curKey, sort)
        }
        adapter.notifyDataSetChanged()
    }

    private fun authenticate(k: Long) {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val i = km.createConfirmDeviceCredentialIntent("Private folder", "Unlock to view")
        if (i == null) { toast("Set a screen lock (PIN/pattern/fingerprint) to use private folders"); refreshSpinner(); return }
        pendingKey = k
        startActivityForResult(i, 3)
    }

    private fun confirm(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Yes") { _, _ -> ok() }.setNegativeButton("Cancel", null).show()
    }

    private fun input(title: String, hint: String, init: String = "", pw: Boolean = false, ok: (String) -> Unit) {
        val e = EditText(this); e.hint = hint; e.setText(init)
        if (pw) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("OK") { _, _ -> ok(e.text.toString()) }.setNegativeButton("Cancel", null).show()
    }

    private fun toggleBulk() {
        if (!bulk) { bulk = true; selected.clear(); bulkBtn.text = "Actions"; toast("Tap items to select, then press Actions"); return }
        if (selected.isEmpty()) { bulk = false; bulkBtn.text = "Bulk"; adapter.notifyDataSetChanged(); return }
        val opts = arrayOf("Delete", "Pin", "Favorite", "Move to folder", "Select all", "Done")
        AlertDialog.Builder(this).setItems(opts) { _, w ->
            when (w) {
                0 -> confirm("Delete ${selected.size} items?") { selected.forEach { db.delete(it) }; endBulk() }
                1 -> { selected.forEach { db.setFlag(it, "pin", true) }; endBulk() }
                2 -> { selected.forEach { db.setFlag(it, "fav", true) }; endBulk() }
                3 -> pickFolder { f -> selected.forEach { db.setFolder(it, f) }; endBulk() }
                4 -> { items.forEach { selected += it.id }; adapter.notifyDataSetChanged() }
                else -> endBulk()
            }
        }.show()
    }

    private fun endBulk() { bulk = false; selected.clear(); bulkBtn.text = "Bulk"; refresh() }

    private fun pickFolder(ok: (Long) -> Unit) {
        val names = arrayOf("Unfiled") + folders.map { path(it) }.toTypedArray()
        AlertDialog.Builder(this).setItems(names) { _, w -> ok(if (w == 0) 0L else folders[w - 1].id) }.show()
    }

    private fun onItem(c: Clip) {
        if (bulk) {
            if (!selected.add(c.id)) selected.remove(c.id)
            adapter.notifyDataSetChanged(); return
        }
        val opts = arrayOf("Copy", if (c.pin) "Unpin" else "Pin", if (c.fav) "Unfavorite" else "Favorite", "Move to folder", "Edit tags", "Delete")
        AlertDialog.Builder(this).setTitle(c.text.take(40)).setItems(opts) { _, w ->
            when (w) {
                0 -> { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("c", c.text)); toast("Copied") }
                1 -> { db.setFlag(c.id, "pin", !c.pin); refresh() }
                2 -> { db.setFlag(c.id, "fav", !c.fav); refresh() }
                3 -> pickFolder { f -> db.setFolder(c.id, f); refresh() }
                4 -> input("Tags (comma separated)", "work,otp", c.tags) { t -> db.setTags(c.id, t.trim()); refresh() }
                5 -> { db.delete(c.id); refresh() }
            }
        }.show()
    }

    private fun newFolder() {
        input("Folder name", "Name") { name ->
            if (name.isBlank()) return@input
            val names = arrayOf("Top level") + folders.map { path(it) }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Parent folder").setItems(names) { _, w ->
                val parent = if (w == 0) 0L else folders[w - 1].id
                AlertDialog.Builder(this).setMessage("Make this a private folder (needs screen lock to open)?")
                    .setPositiveButton("Private") { _, _ -> db.addFolder(name.trim(), parent, true); refreshSpinner() }
                    .setNegativeButton("Normal") { _, _ -> db.addFolder(name.trim(), parent, false); refreshSpinner() }.show()
            }.show()
        }
    }

    private fun askExport() {
        input("Encrypted backup", "Password (leave empty = plain JSON)", "", true) { p ->
            exportPass = p
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, if (p.isEmpty()) "clipboard_backup.json" else "clipboard_backup.bke")
            startActivityForResult(i, 1)
        }
    }

    private fun doImport(bytes: ByteArray, pass: String) {
        try {
            val plain = if (Crypto.isEncrypted(bytes)) Crypto.decrypt(bytes, pass) else bytes
            val n = db.importJson(String(plain, Charsets.UTF_8))
            toast("Imported $n new items"); refreshSpinner(); refresh()
        } catch (e: Exception) { toast("Import failed (wrong password or invalid file)") }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) { if (req == 3) refreshSpinner(); return }
        when (req) {
            1 -> {
                val uri = data?.data ?: return
                try {
                    var b = db.exportJson().toByteArray(Charsets.UTF_8)
                    if (exportPass.isNotEmpty()) b = Crypto.encrypt(b, exportPass)
                    contentResolver.openOutputStream(uri)?.use { it.write(b) }
                    toast("Backup saved")
                } catch (e: Exception) { toast("Export failed: ${e.message}") }
            }
            2 -> {
                val uri = data?.data ?: return
                val b = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                if (Crypto.isEncrypted(b)) input("Backup password", "Password", "", true) { p -> doImport(b, p) } else doImport(b, "")
            }
            3 -> { unlocked += pendingKey; curKey = pendingKey; refreshSpinner(); refresh() }
        }
    }
}
