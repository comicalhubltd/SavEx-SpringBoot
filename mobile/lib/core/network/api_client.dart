import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:pretty_dio_logger/pretty_dio_logger.dart';

class ApiClient {
  static const _baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1',  // Android emulator → localhost
  );

  late final Dio _dio;
  final _storage = const FlutterSecureStorage();

  ApiClient() {
    _dio = Dio(BaseOptions(
      baseUrl: _baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 15),
      headers: {'Content-Type': 'application/json'},
    ));

    _dio.interceptors.addAll([
      _AuthInterceptor(_storage, _dio),
      PrettyDioLogger(requestBody: true, responseBody: true),
    ]);
  }

  // ── Auth ──────────────────────────────────────────────
  Future<Map<String, dynamic>> register(Map<String, String> body) =>
      _post('/auth/register', body);

  Future<Map<String, dynamic>> login(Map<String, String> body) =>
      _post('/auth/login', body);

  // ── Groups ────────────────────────────────────────────
  Future<Map<String, dynamic>> createGroup(Map<String, dynamic> body) =>
      _post('/groups', body);

  Future<Map<String, dynamic>> getGroup(String groupId) =>
      _get('/groups/$groupId');

  Future<Map<String, dynamic>> joinGroup(String inviteCode) =>
      _post('/groups/join/$inviteCode', {});

  Future<Map<String, dynamic>> getOpenGroups({int page = 0, int size = 20}) =>
      _get('/groups/public/open?page=$page&size=$size');

  Future<Map<String, dynamic>> getMyGroups({int page = 0}) =>
      _get('/groups/my?page=$page');

  // ── Wallet ───────────────────────────────────────────
  Future<Map<String, dynamic>> getWallet() => _get('/wallet');

  Future<Map<String, dynamic>> fundWallet(int amountNaira) =>
      _post('/wallet/fund?amountNaira=$amountNaira', {});

  // ── Helpers ──────────────────────────────────────────
  Future<Map<String, dynamic>> _get(String path) async {
    final res = await _dio.get(path);
    return res.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> _post(String path, dynamic body) async {
    final res = await _dio.post(path, data: body);
    return res.data as Map<String, dynamic>;
  }
}

class _AuthInterceptor extends Interceptor {
  final FlutterSecureStorage storage;
  final Dio dio;

  _AuthInterceptor(this.storage, this.dio);

  @override
  Future<void> onRequest(
      RequestOptions options, RequestInterceptorHandler handler) async {
    final token = await storage.read(key: 'access_token');
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
      DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401) {
      // Token expired — try refresh
      final refresh = await storage.read(key: 'refresh_token');
      if (refresh != null) {
        try {
          final res = await Dio().post(
            '${err.requestOptions.baseUrl}/auth/refresh',
            data: {'refreshToken': refresh},
          );
          final newToken = res.data['data']['accessToken'];
          await storage.write(key: 'access_token', value: newToken);
          err.requestOptions.headers['Authorization'] = 'Bearer $newToken';
          final retried = await dio.fetch(err.requestOptions);
          return handler.resolve(retried);
        } catch (_) {
          await storage.deleteAll();
        }
      }
    }
    handler.next(err);
  }
}
