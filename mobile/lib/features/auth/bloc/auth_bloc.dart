import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../core/network/api_client.dart';

// ─── Events ──────────────────────────────────────────────────────────────────

abstract class AuthEvent extends Equatable {
  @override List<Object?> get props => [];
}

class RegisterEvent extends AuthEvent {
  final String fullName, email, phone, password;
  RegisterEvent({required this.fullName, required this.email,
                 required this.phone, required this.password});
  @override List<Object?> get props => [email, phone];
}

class LoginEvent extends AuthEvent {
  final String emailOrPhone, password;
  LoginEvent({required this.emailOrPhone, required this.password});
  @override List<Object?> get props => [emailOrPhone];
}

class LogoutEvent extends AuthEvent {}
class CheckAuthEvent extends AuthEvent {}

// ─── States ──────────────────────────────────────────────────────────────────

abstract class AuthState extends Equatable {
  @override List<Object?> get props => [];
}

class AuthInitial extends AuthState {}
class AuthLoading extends AuthState {}
class AuthAuthenticated extends AuthState {
  final Map<String, dynamic> user;
  AuthAuthenticated(this.user);
  @override List<Object?> get props => [user];
}
class AuthUnauthenticated extends AuthState {}
class AuthError extends AuthState {
  final String message;
  AuthError(this.message);
  @override List<Object?> get props => [message];
}

// ─── Bloc ────────────────────────────────────────────────────────────────────

class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final ApiClient apiClient;
  final _storage = const FlutterSecureStorage();

  AuthBloc({required this.apiClient}) : super(AuthInitial()) {
    on<CheckAuthEvent>(_onCheckAuth);
    on<RegisterEvent>(_onRegister);
    on<LoginEvent>(_onLogin);
    on<LogoutEvent>(_onLogout);
  }

  Future<void> _onCheckAuth(CheckAuthEvent event, Emitter<AuthState> emit) async {
    final token = await _storage.read(key: 'access_token');
    if (token != null) {
      final user = await _storage.read(key: 'user_json') ?? '{}';
      emit(AuthAuthenticated({'cached': true}));
    } else {
      emit(AuthUnauthenticated());
    }
  }

  Future<void> _onRegister(RegisterEvent event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      final res = await apiClient.register({
        'fullName': event.fullName,
        'email': event.email,
        'phone': event.phone,
        'password': event.password,
      });
      await _saveTokens(res['data']);
      emit(AuthAuthenticated(res['data']['user']));
    } catch (e) {
      emit(AuthError(_parseError(e)));
    }
  }

  Future<void> _onLogin(LoginEvent event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      final res = await apiClient.login({
        'emailOrPhone': event.emailOrPhone,
        'password': event.password,
      });
      await _saveTokens(res['data']);
      emit(AuthAuthenticated(res['data']['user']));
    } catch (e) {
      emit(AuthError(_parseError(e)));
    }
  }

  Future<void> _onLogout(LogoutEvent event, Emitter<AuthState> emit) async {
    await _storage.deleteAll();
    emit(AuthUnauthenticated());
  }

  Future<void> _saveTokens(Map data) async {
    await _storage.write(key: 'access_token',  value: data['accessToken']);
    await _storage.write(key: 'refresh_token', value: data['refreshToken']);
  }

  String _parseError(dynamic e) {
    if (e is Exception) return e.toString().replaceAll('Exception: ', '');
    return 'Something went wrong. Please try again.';
  }
}
