import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const CallHistoryApp());

class CallHistoryApp extends StatelessWidget {
  const CallHistoryApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Call History',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF2F5A8F),
        useMaterial3: true,
      ),
      home: const Root(),
    );
  }
}

class Root extends StatefulWidget {
  const Root({super.key});
  @override
  State<Root> createState() => _RootState();
}

class _RootState extends State<Root> with WidgetsBindingObserver {
  static const ch = MethodChannel('callhistory/native');
  bool role = false, phone = false, contacts = false, overlay = false;
  bool loaded = false;
  bool triedRole = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) refresh();
  }

  Future<void> refresh() async {
    try {
      final m = await ch.invokeMapMethod<String, dynamic>('status');
      if (!mounted || m == null) return;
      setState(() {
        role = m['role'] == true;
        phone = m['phone'] == true;
        contacts = m['contacts'] == true;
        overlay = m['overlay'] == true;
        loaded = true;
      });
    } catch (_) {
      if (mounted) setState(() => loaded = true);
    }
  }

  Future<void> askRole() async {
    await ch.invokeMethod('requestRole');
    triedRole = true;
    await refresh();
  }

  Future<void> askPhone() async {
    await ch.invokeMethod('requestPhone');
    await refresh();
  }

  Future<void> askContacts() async {
    await ch.invokeMethod('requestContacts');
    await refresh();
  }

  Future<void> askOverlay() async {
    await ch.invokeMethod('requestOverlay');
  }

  Future<void> openSettings() async {
    await ch.invokeMethod('openRoleSettings');
  }

  bool get ready => role && phone && contacts && overlay;

  @override
  Widget build(BuildContext context) {
    if (!loaded) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Call History')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: ready ? homeView() : setupView(),
        ),
      ),
    );
  }

  List<Widget> setupView() {
    return [
      const Text('Welcome 👋',
          style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold)),
      const SizedBox(height: 8),
      const Text(
          'Call khatam hote hi ye app ek chhota report popup dikhata hai: '
          'kiska call tha (saved naam ya number), kitni der baat hui, aur aaj ke total calls.\n\n'
          'Iske liye neeche ki 4 permissions chahiye. App kisi call ko block ya reject nahi karta.'),
      const SizedBox(height: 20),
      step(1, 'Enable call reporting',
          'Android ka Call Screening role (sirf call events ke liye)', role, askRole),
      step(2, 'Phone state permission',
          'Call start/end pata karne ke liye', phone, askPhone),
      step(3, 'Contacts permission',
          'Saved contact ka naam dikhane ke liye', contacts, askContacts),
      step(4, 'Show popup over other apps',
          'Call ke baad popup dikhane ke liye', overlay, askOverlay),
      if (triedRole && !role)
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Permission not enabled',
                  style: TextStyle(
                      color: Colors.red, fontWeight: FontWeight.bold)),
              const SizedBox(height: 6),
              Row(children: [
                FilledButton(onPressed: askRole, child: const Text('Retry')),
                const SizedBox(width: 12),
                OutlinedButton(
                    onPressed: openSettings, child: const Text('Open Settings')),
              ]),
            ],
          ),
        ),
    ];
  }

  Widget step(int n, String title, String sub, bool done, VoidCallback onTap) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(child: Text('$n')),
        title: Text(title),
        subtitle: Text(sub),
        trailing: done
            ? const Icon(Icons.check_circle, color: Colors.green)
            : FilledButton(onPressed: onTap, child: const Text('Enable')),
      ),
    );
  }

  List<Widget> homeView() {
    return [
      Card(
        color: const Color(0xFFE8F6EC),
        child: const ListTile(
          leading: Icon(Icons.check_circle, color: Colors.green, size: 32),
          title: Text('Call reporting: Enabled',
              style: TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text('App incoming/outgoing call events ka wait kar raha hai.'),
        ),
      ),
      const SizedBox(height: 16),
      const Text('Test kaise karein',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
      const SizedBox(height: 6),
      const Text(
          'Kisi ko call karo (ya kisi ka call lo) aur call khatam karo. '
          'Report popup neeche se upar slide hoga aur 5 second baad apne aap band ho jayega.\n\n'
          'Saved number ho to uska naam dikhega, nahi to number dikhega.\n\n'
          'Agar popup na dikhe: phone ki battery settings me is app ko "Unrestricted" / '
          '"No restrictions" karo (Xiaomi, Oppo, Vivo me zaroori hota hai).'),
      const SizedBox(height: 20),
      OutlinedButton.icon(
        onPressed: openSettings,
        icon: const Icon(Icons.settings),
        label: const Text('Android settings kholo (role change/revoke)'),
      ),
    ];
  }
}
