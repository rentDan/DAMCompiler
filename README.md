# DAMLanguage
A compiler for the DAM langauge.

Simple how it works:
- Modify samples/hello.dam and run the program.
- Ultimately it outputs a executable hello.class file.

I dont have a documentation on the language, 
but the hello.dam file has examples of all
features I implemented.

To run:
- Add `samples/hello.dam` as an argument when running
- The main method is in damlang.DamCompiler
- The only problem I ran into was with jasmin.jar,
  I had to make sure the library was correctly loaded.
