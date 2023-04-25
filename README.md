# Fragment Search (in Fragment Sets)

A simple spike project to solve [LPS-142817](https://issues.liferay.com/browse/LPS-142817), with a (as you expect from me) _very_ crude UI.

A proper UI would require to duplicate a lot of _internal_ classes from the original Fragment Administration, which I frowned upon. 

## How to use

Clone into a Liferay Workspace's `modules` directory and build. 

Tested on DXP 7.4 U70, but should work on almost every other build, as well as on Liferay CE.  For Liferay CE, set the appropriate dependency in this module's `build.gradle`. 

This plugin uses the target platform set in the workspace's `gradle.properties` (`liferay.workspace.product`)
