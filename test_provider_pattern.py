print("Testing ClonerInitProvider pattern vs Application delegation pattern...")
print("Insight: Replacing <application android:name> breaks (MyApplication) getApplication() casts.")
print("Using ClonerInitProvider with initOrder=999999 runs BEFORE Application.onCreate and NEVER causes ClassCastException!")
