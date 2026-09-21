require 'xcodeproj'

project_path = 'ios/Runner.xcodeproj'
unless File.exist?(project_path)
  puts "Error: #{project_path} not found!"
  exit 1
end

project = Xcodeproj::Project.open(project_path)

if project.targets.any? { |t| t.name == 'TimetableWidgetExtension' }
  puts "TimetableWidgetExtension target already exists."
  exit 0
end

puts "Configuring TimetableWidgetExtension target in #{project_path}..."

app_target = project.targets.find { |t| t.name == 'Runner' }
unless app_target
  puts "Error: Runner target not found!"
  exit 1
end

widget_target = project.new_target(
  :app_extension,
  'TimetableWidgetExtension',
  :ios,
  '16.0'
)

group = project.main_group.find_subpath('TimetableWidget', true)
source_files = [
  'ios/TimetableWidget/TimetableWidget.swift',
  'ios/TimetableWidget/TimetableWidgetBundle.swift'
]

source_files.each do |file_path|
  if File.exist?(file_path)
    file_ref = group.new_file(File.basename(file_path))
    widget_target.add_file_references([file_ref])
  end
end

widget_target.build_configurations.each do |config|
  config.build_settings['PRODUCT_NAME'] = 'TimetableWidgetExtension'
  config.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] = 'com.sked.skedApp.TimetableWidget'
  config.build_settings['INFOPLIST_FILE'] = 'TimetableWidget/Info.plist'
  config.build_settings['SWIFT_VERSION'] = '5.0'
  config.build_settings['TARGETED_DEVICE_FAMILY'] = '1,2'
  config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '16.0'
  config.build_settings['CODE_SIGNING_ALLOWED'] = 'NO'
  config.build_settings['CODE_SIGNING_REQUIRED'] = 'NO'
  config.build_settings['CODE_SIGN_IDENTITY'] = ''
  config.build_settings['EXPANDED_CODE_SIGN_IDENTITY'] = ''
  config.build_settings['GENERATE_INFOPLIST_FILE'] = 'NO'
  config.build_settings['CURRENT_PROJECT_VERSION'] = '3'
  config.build_settings['MARKETING_VERSION'] = '1.2.0'
end

widget_target.add_system_frameworks(['WidgetKit', 'SwiftUI'])
app_target.add_dependency(widget_target)

embed_phase = app_target.copy_files_build_phases.find { |p| p.name == 'Embed Foundation Extensions' || p.dst_subfolder_spec == 13 }
unless embed_phase
  embed_phase = app_target.new_copy_files_build_phase('Embed Foundation Extensions')
  embed_phase.dst_subfolder_spec = 13
  embed_phase.dst_path = ''
end

file_ref = widget_target.product_reference
build_file = embed_phase.add_file_reference(file_ref)
build_file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

project.save
puts "Successfully configured TimetableWidgetExtension target in Runner.xcodeproj!"
