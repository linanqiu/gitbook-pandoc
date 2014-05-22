gitbook-pandoc
==============

Converts Gitbook directory to LaTeX using Pandoc

## Dependencies

- Java 1.7
- Apache Commons FileUtils

## Quick Start

Download the release and `header.tex`, and place them in the same folder. 

Run as such

```
$ java -jar GitbookToPandoc.jar source_directory target_directory
```

For example, if my folder directory is like this

- Project
    + GitbookFolder
        * _book
        * ch05
            - readme.md
            - somechapter.md
            - someotherchapter.md
        * ch07
            - readme.md
            - somemorechapter.md
        * readme.md
        * summary.md
    + TargetFolder
    + GitbookToPandoc.jar
    + header.tex

Run as 

```
$ java -jar GitbookToPandoc.jar ./GitbookFolder ./TargetFolder
```

- Make sure that `header.tex` exists
- Right now, it only converts `.md` to `.tex`. Any other extensions or formats are unsupported. Come on man I only have one day.