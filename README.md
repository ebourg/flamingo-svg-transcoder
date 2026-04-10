Flamingo SVG Transcoder
=======================

[![Build Status](https://secure.travis-ci.org/ebourg/flamingo-svg-transcoder.svg)](http://travis-ci.org/ebourg/flamingo-svg-transcoder)
[![Coverage Status](https://coveralls.io/repos/github/ebourg/flamingo-svg-transcoder/badge.svg?branch=master)](https://coveralls.io/github/ebourg/flamingo-svg-transcoder?branch=master)
[![License](https://img.shields.io/badge/license-BSD/Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

This tool let you transform SVG images into pure Java2D code with no external dependencies.
The following features are supported:

 * All geometric shapes and paths
 * Text
 * Transformations
 * Colors and gradients

These SVG features are not supported:

 * Animations
 * Images
 * Filter effects
 * Masks and Clipping
 * Patterns
 * Markers

The SVG Transcoder successfully converted more than 5000 icons from various GNOME and KDE themes.
On average the generated .class file with gzip compression is about 30% bigger than the corresponding
svgz image. With pack200+gzip compression it's about 40% smaller.

The SVG transcoder is available as an Ant task or a GUI application. See the [website](http://ebourg.github.io/flamingo-svg-transcoder/)
for more details.


### Changes

#### Version 1.2 (2016-07-06)

* Fixed the nested transformations (#3)
* Fixed the opacity of the nested elements (thanks to Paolo Ferracin)
* The Ant task now throws an exception when an invalid naming strategy is specified.

#### Version 1.1.1 (2012-11-06)

* Fixed the transcoding of general paths

#### Version 1.1 (2012-09-10)

* Fixed the incrementation of the gradient stop fractions (#1)
* The PINK and ORANGE colors are now transcoded by name.


#### Version 1.0 (2011-12-15)

* Initial release


### Credits

The SVG Transcoder has been originaly developped by Kirill Grouchnikov as part of the Flamingo project
(see his [blog](https://web.archive.org/web/20061106160640/http://weblogs.java.net/blog/kirillcool/archive/2006/10/svg_and_java_ui_3.html)
for more information). It leverages the [Apache Batik project](http://xmlgraphics.apache.org/batik/)
for parsing the SVG files.


### License

BSD (for the original Flamingo files), Apache License 2.0 (for the new files)
