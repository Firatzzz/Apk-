package com.example.catatanku

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val ID = Locale("id", "ID")
private fun rp(n: Long) = NumberFormat.getCurrencyInstance(ID).apply { maximumFractionDigits = 0 }.format(n)
private fun fmtDate(m: Long) = SimpleDateFormat("EEEE, d MMM yyyy", ID).format(Date(m))
private fun fmtTime(m: Long) = SimpleDateFormat("HH:mm", ID).format(Date(m))

class AppState(private val store: Store) {
    val tasks = mutableStateListOf<Task>().apply { addAll(store.loadTasks()) }
    val txs = mutableStateListOf<Tx>().apply { addAll(store.loadTxs()) }
    fun save() = store.save(tasks.toList(), txs.toList())
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppState(Store(this))
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { App(state) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(s: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(if (tab == 0) "Kegiatan" else "Keuangan", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Event, null) }, label = { Text("Kegiatan") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("Keuangan") })
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton({ showDialog = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (tab == 0) "Kegiatan" else "Transaksi") })
        }
    ) { pad ->
        Box(Modifier.padding(pad)) { if (tab == 0) TaskScreen(s) else FinanceScreen(s) }
    }
    if (showDialog) {
        if (tab == 0) TaskDialog({ showDialog = false }) { s.tasks.add(it); s.save(); showDialog = false }
        else TxDialog({ showDialog = false }) { s.txs.add(it); s.save(); showDialog = false }
    }
}

@Composable
fun EmptyHint(text: String) = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TaskScreen(s: AppState) {
    val ctx = LocalContext.current
    val list = s.tasks.sortedWith(compareBy({ it.done }, { it.time }))
    if (list.isEmpty()) return EmptyHint("Belum ada kegiatan. Tekan + untuk menambah.")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { t ->
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                containerColor = if (t.done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(t.done, { c ->
                        val i = s.tasks.indexOfFirst { it.id == t.id }
                        s.tasks[i] = t.copy(done = c); s.save() })
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null)
                        if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        AssistChip({}, label = { Text("${fmtDate(t.time)} • ${fmtTime(t.time)}") },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) })
                        if (t.location.isNotBlank()) AssistChip({
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(t.location)))) }
                        }, label = { Text(t.location) }, leadingIcon = { Icon(Icons.Default.Place, null, Modifier.size(18.dp)) })
                    }
                    IconButton({ s.tasks.removeAll { it.id == t.id }; s.save() }) { Icon(Icons.Default.Delete, "Hapus") }
                }
            }
        }
    }
}

@Composable
fun FinanceScreen(s: AppState) {
    val income = s.txs.filter { it.income }.sumOf { it.amount }
    val expense = s.txs.filter { !it.income }.sumOf { it.amount }
    val list = s.txs.sortedByDescending { it.time }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Text("Saldo", style = MaterialTheme.typography.labelLarge)
                    Text(rp(income - expense), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Column { Text("Pemasukan", style = MaterialTheme.typography.labelMedium); Text(rp(income), fontWeight = FontWeight.SemiBold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Pengeluaran", style = MaterialTheme.typography.labelMedium); Text(rp(expense), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        if (list.isEmpty()) item { Text("Belum ada transaksi.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(list, key = { it.id }) { t ->
            ListItem(
                modifier = Modifier.then(Modifier),
                colors = ListItemDefaults.colors(MaterialTheme.colorScheme.surfaceContainerHigh),
                leadingContent = { Icon(if (t.income) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null,
                    tint = if (t.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
                headlineContent = { Text(t.title, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("${t.category} • ${fmtDate(t.time)}") },
                trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (t.income) "+" else "-") + rp(t.amount), fontWeight = FontWeight.Bold,
                        color = if (t.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    IconButton({ s.txs.removeAll { it.id == t.id }; s.save() }) { Icon(Icons.Default.Delete, "Hapus") }
                } }
            )
        }
    }
}

private fun utcMidnight(m: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = m }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l[Calendar.YEAR], l[Calendar.MONTH], l[Calendar.DAY_OF_MONTH]) }.timeInMillis
}
private fun mergeDate(m: Long, utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply { timeInMillis = m
        set(u[Calendar.YEAR], u[Calendar.MONTH], u[Calendar.DAY_OF_MONTH]) }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePicker(millis: Long, onChange: (Long) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ pickDate = true }, Modifier.weight(1f)) { Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(6.dp)); Text(SimpleDateFormat("d MMM yyyy", ID).format(Date(millis))) }
        OutlinedButton({ pickTime = true }) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text(fmtTime(millis)) }
    }
    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = utcMidnight(millis))
        DatePickerDialog({ pickDate = false }, confirmButton = {
            TextButton({ st.selectedDateMillis?.let { onChange(mergeDate(millis, it)) }; pickDate = false }) { Text("OK") } }
        ) { DatePicker(st) }
    }
    if (pickTime) {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val ts = rememberTimePickerState(c[Calendar.HOUR_OF_DAY], c[Calendar.MINUTE], true)
        AlertDialog({ pickTime = false },
            confirmButton = { TextButton({
                onChange(c.apply { set(Calendar.HOUR_OF_DAY, ts.hour); set(Calendar.MINUTE, ts.minute) }.timeInMillis)
                pickTime = false }) { Text("OK") } },
            text = { TimePicker(ts) })
    }
}

@Composable
fun TaskDialog(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf("") }
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    AlertDialog(onDismiss, title = { Text("Kegiatan baru") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Nama kegiatan") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("Catatan") })
            DateTimePicker(millis) { millis = it }
            OutlinedTextField(loc, { loc = it }, label = { Text("Lokasi (alamat / nama tempat)") },
                leadingIcon = { Icon(Icons.Default.Place, null) })
        } },
        confirmButton = { TextButton({ if (title.isNotBlank()) onSave(Task(title = title.trim(), note = note.trim(), time = millis, location = loc.trim())) }) { Text("Simpan") } },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxDialog(onDismiss: () -> Unit, onSave: (Tx) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("Makanan") }
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cats = if (income) listOf("Gaji", "Bonus", "Lainnya") else listOf("Makanan", "Transport", "Belanja", "Tagihan", "Hiburan", "Lainnya")
    AlertDialog(onDismiss, title = { Text("Transaksi baru") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!income, { income = false; cat = "Makanan" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Pengeluaran") }
                SegmentedButton(income, { income = true; cat = "Gaji" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Pemasukan") }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Keterangan") }, singleLine = true)
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Jumlah (Rp)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEach { FilterChip(cat == it, { cat = it }, { Text(it) }) } }
            DateTimePicker(millis) { millis = it }
        } },
        confirmButton = { TextButton({
            val a = amount.toLongOrNull() ?: 0L
            if (title.isNotBlank() && a > 0) onSave(Tx(title = title.trim(), amount = a, income = income, category = cat, time = millis))
        }) { Text("Simpan") } },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } })
}
