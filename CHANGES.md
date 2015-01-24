# Changes

This document describes the changes included in each release of Rapture JSON.

## Version 1.0.1
 - The `DynamicWorkaround` implicit class has been added to work around a problem accessing members called `self` in Scala 2.10.
 - Extractors now support supression of errors, to support extraction of potentially nonexistent values into `Option` types

## Version 1.0.2
 - Fix for serious infinite recursion issue when accessing a JSON object key, and indexing an array in the same expression (`applyDynamic`).
 - Removed unnecessary `DynamicWorkaround` for Scala 2.11 (where it isn't necessary).

## Version 1.0.3
 - Automatically convert between different JSON backends just by wrapping in `Json`.
 - Fix regression on nested case class extraction, with "double definition" error occurring during macro expansion (#17).
 - Support extraction of `BigInt`s and `BigDecimal`s
 - Tidyup of serialization type classes

## Version 1.0.4
 - Fixed serious bug with pattern matching
 - Simplified definitions of extractors and serializers, and support for auto-conversion between different backends on extraction

## Version 1.0.5
 - Fixed exponential build times (in the nesting depth of extracted case classes)
 - Simpler definitions of serializers

## Version 1.0.6
 - Removed spurious deprecation warning when using macros
 - Allow automatic conversion on extraction to `Json` type
 - Workaround Scala issue SI-8947 in Scala 2.11.4

## Version 1.0.7
 - Support for addition of `Option[T]` values to maps
 - Extraction into `Try`s
 - Compile-time checking of Json literals

## Version 1.0.8
 - Renamed implicit imports from different backends, so later imports shadow earlier ones, rather than being ambiguous
 - Fixed issue with successful compilation of unextractable case class parameter values

## Version 1.1.0
 - Fixed return type of `parse` method
 - Better debugging for case class extractor generation failures
 - Added convenience methods for composing serializers
 - Upgrade to Rapture Core 1.1.0
 - Provided a `|+|` method for combining extraction exceptions, which now form a semigroup
 - toString produces "compact" output now. Use import formatters.humanReadable._ and Json.format(json) for the old-style output
 - Fixed #23. Extraction of `Option`s now works again
 - Cleanup of public API, and concealment of internal APIs in an `internal` package
 - Fixes for various incorrectly-reported exceptions

## Version 1.2.0 (not yet released)
 - Fix for type of exception thrown in Jackson integration
 - Support for extraction and serialization of value classes
 - Added support for XPath-style \ and \\ operators
