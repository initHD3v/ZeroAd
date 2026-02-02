// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'models.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Threat _$ThreatFromJson(Map<String, dynamic> json) => Threat(
  type: json['type'] as String,
  severity: json['severity'] as String,
  description: json['description'] as String,
);

Map<String, dynamic> _$ThreatToJson(Threat instance) => <String, dynamic>{
  'type': instance.type,
  'severity': instance.severity,
  'description': instance.description,
};

AppThreatInfo _$AppThreatInfoFromJson(Map<String, dynamic> json) =>
    AppThreatInfo(
      packageName: json['packageName'] as String,
      appName: json['appName'] as String,
      isSystemApp: json['isSystemApp'] as bool,
      detectedThreats: (json['detectedThreats'] as List<dynamic>)
          .map((e) => Threat.fromJson(e as Map<String, dynamic>))
          .toList(),
      zeroScore: (json['zeroScore'] as num?)?.toInt() ?? 0,
    );

Map<String, dynamic> _$AppThreatInfoToJson(AppThreatInfo instance) =>
    <String, dynamic>{
      'packageName': instance.packageName,
      'appName': instance.appName,
      'isSystemApp': instance.isSystemApp,
      'detectedThreats': instance.detectedThreats,
      'zeroScore': instance.zeroScore,
    };

ScanResultModel _$ScanResultModelFromJson(Map<String, dynamic> json) =>
    ScanResultModel(
      totalInstalledPackages: (json['totalInstalledPackages'] as num).toInt(),
      suspiciousPackagesCount: (json['suspiciousPackagesCount'] as num).toInt(),
      threats: (json['threats'] as List<dynamic>)
          .map((e) => AppThreatInfo.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$ScanResultModelToJson(ScanResultModel instance) =>
    <String, dynamic>{
      'totalInstalledPackages': instance.totalInstalledPackages,
      'suspiciousPackagesCount': instance.suspiciousPackagesCount,
      'threats': instance.threats,
    };
