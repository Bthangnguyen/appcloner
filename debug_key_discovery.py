import struct, zipfile, os, sys

sys.stdout.reconfigure(encoding='utf-8')

# We need to simulate what happens when ClonePipeline processes Device Info HW
# The user has it installed on their phone, and our app reads it from appInfo.sourceDir
# Let's check if there's a cached APK or we need to figure it out differently

# The actual issue: when the cloned app launches, it crashes immediately.
# This could be caused by:
# 1. Application class not found (manifest android:name attribute changed incorrectly)
# 2. resources.arsc package name mismatch (R.class references broken)
# 3. Missing split APK resources/native libs
# 4. ContentProvider authority conflicts

# Let's simulate the AxmlEditor logic with a sample manifest to verify correctness

# First, let's check what the AxmlEditor does to component names
# Rule: If class starts with "." -> expand to originalPackage + class
# Rule: If class is in dexClasses -> keep unchanged
# Rule: package attribute -> change to newPackage

# The CRITICAL issue: In Android, resource IDs (R.java) are bound to the PACKAGE NAME at compile time
# When we change package in manifest but NOT in DEX, the app's code still references
# R.layout.activity_main etc. which are internally stored as 0x7f0xxxxx integers.
# These integers are looked up in resources.arsc -> Package chunk -> package name must match
# the runtime package name for the AssetManager to resolve them.
#
# BUT WAIT: The AssetManager actually resolves resources using the resources.arsc package name,
# NOT the manifest package name. The manifest package = application ID (for installation),
# while resources.arsc package = R.class package (for resource resolution).
#
# If they differ, Android uses the APPLICATION ID for process/permissions,
# but the ARSC PACKAGE for resource resolution.
#
# KEY INSIGHT: resources.arsc package name MUST STAY AS ORIGINAL to match R.class in DEX!
# Only the manifest package should change!

print("=" * 60)
print("KEY DISCOVERY: resources.arsc package MUST NOT be changed!")
print("=" * 60)
print()
print("Reason: R.java class in DEX references resources via integer IDs (0x7fXXYYZZ)")
print("These IDs map to resource entries in resources.arsc indexed by package name.")
print("If resources.arsc package = 'ru.andr7e.deviceinfohw' but DEX R.class expects")
print("'ru.andr7e.deviceinfohw', changing ARSC to '.clone1' breaks the R.class -> ARSC lookup!")
print()
print("The manifest package = APPLICATION ID (for Android installer)")
print("The resources.arsc package = R.class resource table (for Android runtime)")
print("These two can be DIFFERENT in a valid APK.")
print()
print("SOLUTION: Keep resources.arsc package as ORIGINAL, only change manifest package!")
