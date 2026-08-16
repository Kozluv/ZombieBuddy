namespace :authors do
  desc "Add a new author to the database"
  task :add do
    _, id, name, key = ARGV
    msg = "Usage: rake authors:add <id> <name> <key>"
    raise msg unless id =~ /^\d+$/
    raise msg unless name.size > 0
    raise msg unless key.size == 64

    data = JSON.parse(File.read("authors.json"))
    data['authors'] << { "id" => id.to_i, "name" => name, "keys" => [key] }
    File.write("authors.json", JSON.pretty_generate(data))

    Rake::Task["authors:sign"].invoke
    exit 0
  end

  desc 'sign authors'
  task :sign do
    Dir.chdir("java") do
      sh "gradle signAuthorsJson"
    end
  end
end
