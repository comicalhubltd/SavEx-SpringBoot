import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/theme/app_theme.dart';
import '../auth/bloc/auth_bloc.dart';
import '../groups/bloc/group_bloc.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    context.read<GroupBloc>().add(LoadMyGroupsEvent());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SavEx', style: TextStyle(fontWeight: FontWeight.w700)),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(Icons.logout_outlined),
            onPressed: () => context.read<AuthBloc>().add(LogoutEvent()),
          ),
        ],
      ),
      body: IndexedStack(
        index: _tab,
        children: const [
          _GroupsTab(),
          _WalletTab(),
          _ProfileTab(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.group_outlined),   selectedIcon: Icon(Icons.group),   label: 'Groups'),
          NavigationDestination(icon: Icon(Icons.account_balance_wallet_outlined), selectedIcon: Icon(Icons.account_balance_wallet), label: 'Wallet'),
          NavigationDestination(icon: Icon(Icons.person_outline),   selectedIcon: Icon(Icons.person),   label: 'Profile'),
        ],
      ),
      floatingActionButton: _tab == 0 ? FloatingActionButton.extended(
        onPressed: () => Navigator.pushNamed(context, '/groups/create'),
        icon: const Icon(Icons.add),
        label: const Text('New Group'),
      ) : null,
    );
  }
}

class _GroupsTab extends StatelessWidget {
  const _GroupsTab();

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<GroupBloc, GroupState>(
      builder: (ctx, state) {
        if (state is GroupLoading) return const Center(child: CircularProgressIndicator());
        if (state is GroupError)   return Center(child: Text(state.message));

        final groups = state is MyGroupsLoaded ? state.groups : [];

        if (groups.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.group_add, size: 64, color: Colors.grey),
                const SizedBox(height: 12),
                const Text('No groups yet', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                const Text('Create or join a savings circle to get started.', textAlign: TextAlign.center),
                const SizedBox(height: 20),
                OutlinedButton.icon(
                  onPressed: () => Navigator.pushNamed(ctx, '/groups/join'),
                  icon: const Icon(Icons.link),
                  label: const Text('Join with invite code'),
                ),
              ],
            ),
          );
        }

        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: groups.length,
          separatorBuilder: (_, __) => const SizedBox(height: 12),
          itemBuilder: (_, i) => _GroupCard(group: groups[i]),
        );
      },
    );
  }
}

class _GroupCard extends StatelessWidget {
  final Map group;
  const _GroupCard({required this.group});

  @override
  Widget build(BuildContext context) {
    final progress = (group['currentCycle'] as int) / (group['totalCycles'] as int);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Expanded(child: Text(group['name'], style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16))),
              _StatusChip(group['status']),
            ]),
            const SizedBox(height: 8),
            Text('₦${group['contributionNaira']} · ${group['cycleFrequency']}',
                style: TextStyle(color: Colors.grey[600])),
            const SizedBox(height: 10),
            LinearProgressIndicator(value: progress, minHeight: 6,
                borderRadius: BorderRadius.circular(4)),
            const SizedBox(height: 4),
            Text('Cycle ${group['currentCycle']} of ${group['totalCycles']}',
                style: const TextStyle(fontSize: 12, color: Colors.grey)),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String status;
  const _StatusChip(this.status);

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      'ACTIVE'    => Colors.green,
      'OPEN'      => Colors.blue,
      'COMPLETED' => Colors.grey,
      _           => Colors.orange,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: color.withOpacity(0.12), borderRadius: BorderRadius.circular(8)),
      child: Text(status, style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w600)),
    );
  }
}

class _WalletTab extends StatelessWidget {
  const _WalletTab();
  @override
  Widget build(BuildContext context) => const Center(child: Text('Wallet coming soon'));
}

class _ProfileTab extends StatelessWidget {
  const _ProfileTab();
  @override
  Widget build(BuildContext context) => const Center(child: Text('Profile coming soon'));
}
