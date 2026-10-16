import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import '../features/auth/screens/login_screen.dart';
import '../features/dashboard/dashboard_screen.dart';

class AppRouter {
  static final router = GoRouter(
    initialLocation: '/login',
    routes: [
      GoRoute(path: '/login',     builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/dashboard', builder: (_, __) => const DashboardScreen()),
      GoRoute(path: '/groups/create', builder: (_, __) => const Placeholder()),
      GoRoute(path: '/groups/join',   builder: (_, __) => const Placeholder()),
      GoRoute(path: '/register',      builder: (_, __) => const Placeholder()),
    ],
  );
}
