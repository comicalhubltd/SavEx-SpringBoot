import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../bloc/auth_bloc.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _identifier = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _identifier.dispose();
    _password.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_form.currentState!.validate()) return;
    context.read<AuthBloc>().add(LoginEvent(
          emailOrPhone: _identifier.text.trim(),
          password: _password.text,
        ));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: BlocConsumer<AuthBloc, AuthState>(
        listener: (context, state) {
          if (state is AuthError) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(state.message), backgroundColor: Colors.red),
            );
          }
        },
        builder: (context, state) {
          return SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _form,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: 40),
                    Text('Welcome back 👋',
                        style: theme.textTheme.headlineMedium
                            ?.copyWith(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 8),
                    Text('Sign in to your SavEx account',
                        style: theme.textTheme.bodyLarge
                            ?.copyWith(color: Colors.grey)),
                    const SizedBox(height: 40),
                    TextFormField(
                      controller: _identifier,
                      decoration: const InputDecoration(
                          labelText: 'Email or Phone',
                          prefixIcon: Icon(Icons.person_outline)),
                      keyboardType: TextInputType.emailAddress,
                      validator: (v) =>
                          (v == null || v.isEmpty) ? 'Required' : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _password,
                      obscureText: _obscure,
                      decoration: InputDecoration(
                        labelText: 'Password',
                        prefixIcon: const Icon(Icons.lock_outline),
                        suffixIcon: IconButton(
                          icon: Icon(_obscure
                              ? Icons.visibility_off
                              : Icons.visibility),
                          onPressed: () => setState(() => _obscure = !_obscure),
                        ),
                      ),
                      validator: (v) =>
                          (v == null || v.length < 8) ? 'Min 8 characters' : null,
                    ),
                    const SizedBox(height: 32),
                    ElevatedButton(
                      onPressed:
                          state is AuthLoading ? null : _submit,
                      child: state is AuthLoading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.white))
                          : const Text('Sign In'),
                    ),
                    const SizedBox(height: 16),
                    Center(
                      child: TextButton(
                        onPressed: () =>
                            Navigator.pushNamed(context, '/register'),
                        child: const Text("Don't have an account? Register"),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
