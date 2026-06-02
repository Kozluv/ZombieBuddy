def gradle_test_task_for(tests)
  return "test" unless tests

  case ENV["TEST_SUITE"]&.downcase
  when "patched" then return "test_patched"
  when "vanilla" then return "test_vanilla"
  when "unit"     then return "unitTest"
  end

  name = tests.to_s.split("#").first
  name = name.split("/").last if name.include?("/")
  name = name.split(".").last if name.include?(".")

  case name
  when /\APatchedTest/, /\APatched/ then "test_patched"
  when /\AVanillaTest/, /\AVanilla/ then "test_vanilla"
  else "unitTest"
  end
end

def run_tests(task = nil)
  env = {
    "ZB_VERBOSITY" => (ENV['ZB_VERBOSITY'] || "2"), # for unit tests that run without Agent, but with Logger
  }
  cp_root = File.join(PROJECT_ROOT, "versions/unstable/java")
  cp = [File.join(cp_root, "projectzomboid.jar")].join(",")
  props = {
    :gameClasspath => cp,
    :showStreams   => true,
  }

  task ||= gradle_test_task_for(ENV["TESTS"])
  cmd = ["gradle", task, "--info", *props.map { |k, v| "-P#{k}=#{v}" }]

  if ENV["TESTS"]
    cmd << "--tests" << ENV["TESTS"]
  end

  Dir.chdir("java") do
    sh env, *cmd
  end
end

namespace :test do
  desc "run unit tests (optional: TESTS=UtilsTest or TESTS=UtilsTest#methodName)"
  task :unit do
    run_tests "unitTest"
  end

  desc "run patched tests with javaagent (optional: TESTS=PatchedTestSkipOn)"
  task :patched do
    run_tests "test_patched"
  end

  desc "run vanilla tests without javaagent (optional: TESTS=VanillaTestFieldValue)"
  task :vanilla do
    run_tests "test_vanilla"
  end
end

desc "run tests (optional: TESTS=PatchedTestSkipOn; TEST_SUITE=patched|vanilla|unit to force suite)"
task :test do
  run_tests
end
