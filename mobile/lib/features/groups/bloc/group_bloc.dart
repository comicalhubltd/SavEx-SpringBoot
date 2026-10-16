import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';
import '../../../core/network/api_client.dart';

// ─── Events ──────────────────────────────────────────────────────────────────

abstract class GroupEvent extends Equatable {
  @override List<Object?> get props => [];
}

class LoadMyGroupsEvent extends GroupEvent {}
class LoadOpenGroupsEvent extends GroupEvent {}

class CreateGroupEvent extends GroupEvent {
  final Map<String, dynamic> data;
  CreateGroupEvent(this.data);
  @override List<Object?> get props => [data];
}

class JoinGroupEvent extends GroupEvent {
  final String inviteCode;
  JoinGroupEvent(this.inviteCode);
  @override List<Object?> get props => [inviteCode];
}

// ─── States ──────────────────────────────────────────────────────────────────

abstract class GroupState extends Equatable {
  @override List<Object?> get props => [];
}

class GroupInitial extends GroupState {}
class GroupLoading extends GroupState {}

class MyGroupsLoaded extends GroupState {
  final List<dynamic> groups;
  MyGroupsLoaded(this.groups);
  @override List<Object?> get props => [groups];
}

class OpenGroupsLoaded extends GroupState {
  final List<dynamic> groups;
  OpenGroupsLoaded(this.groups);
  @override List<Object?> get props => [groups];
}

class GroupActionSuccess extends GroupState {
  final String message;
  final Map<String, dynamic> group;
  GroupActionSuccess(this.message, this.group);
  @override List<Object?> get props => [message];
}

class GroupError extends GroupState {
  final String message;
  GroupError(this.message);
  @override List<Object?> get props => [message];
}

// ─── Bloc ────────────────────────────────────────────────────────────────────

class GroupBloc extends Bloc<GroupEvent, GroupState> {
  final ApiClient apiClient;

  GroupBloc({required this.apiClient}) : super(GroupInitial()) {
    on<LoadMyGroupsEvent>(_onLoadMyGroups);
    on<LoadOpenGroupsEvent>(_onLoadOpenGroups);
    on<CreateGroupEvent>(_onCreateGroup);
    on<JoinGroupEvent>(_onJoinGroup);
  }

  Future<void> _onLoadMyGroups(LoadMyGroupsEvent e, Emitter<GroupState> emit) async {
    emit(GroupLoading());
    try {
      final res = await apiClient.getMyGroups();
      final groups = res['data']['content'] as List;
      emit(MyGroupsLoaded(groups));
    } catch (err) {
      emit(GroupError(err.toString()));
    }
  }

  Future<void> _onLoadOpenGroups(LoadOpenGroupsEvent e, Emitter<GroupState> emit) async {
    emit(GroupLoading());
    try {
      final res = await apiClient.getOpenGroups();
      final groups = res['data']['content'] as List;
      emit(OpenGroupsLoaded(groups));
    } catch (err) {
      emit(GroupError(err.toString()));
    }
  }

  Future<void> _onCreateGroup(CreateGroupEvent e, Emitter<GroupState> emit) async {
    emit(GroupLoading());
    try {
      final res = await apiClient.createGroup(e.data);
      emit(GroupActionSuccess('Group created!', res['data']));
    } catch (err) {
      emit(GroupError(err.toString()));
    }
  }

  Future<void> _onJoinGroup(JoinGroupEvent e, Emitter<GroupState> emit) async {
    emit(GroupLoading());
    try {
      final res = await apiClient.joinGroup(e.inviteCode);
      emit(GroupActionSuccess('Joined group successfully!', res['data']));
    } catch (err) {
      emit(GroupError(err.toString()));
    }
  }
}
