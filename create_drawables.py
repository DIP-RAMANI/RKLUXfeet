import os

base_dir = r"c:\Users\Vatsal kotecha\AndroidStudioProjects\androidhack\app\src\main\res\drawable"

drawables = {
    "ic_search.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#8D8D8D" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M11,19C15.418,19 19,15.418 19,11C19,6.582 15.418,3 11,3C6.582,3 3,6.582 3,11C3,15.418 6.582,19 11,19Z" />
  <path android:fillColor="#00000000" android:strokeColor="#8D8D8D" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M21,21L16.65,16.65" />
</vector>""",
    "ic_bag.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M6,6h12v15H6z" />
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M9,6V4a3,3 0,0 1,6 0v2" />
</vector>""",
    "ic_home.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M3,9l9,-7 9,7v11a2,2 0,0 1,-2 2H5a2,2 0,0 1,-2 -2z" />
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M9,22V12h6v10" />
</vector>""",
    "ic_shop.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M4,6h16v16H4z" />
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M16,10a4,4 0,0 1,-8 0" />
</vector>""",
    "ic_wishlist.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M20.84,4.61a5.5,5.5 0,0 0,-7.78 0L12,5.67l-1.06,-1.06a5.5,5.5 0,0 0,-7.78 7.78l1.06,1.06L12,21.23l7.78,-7.78 1.06,-1.06a5.5,5.5 0,0 0,0 -7.78z" />
</vector>""",
    "ic_profile.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M20,21v-2a4,4 0,0 0,-4 -4H8a4,4 0,0 0,-4 4v2" />
  <circle android:cx="12" android:cy="7" android:r="4" android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" />
</vector>""",
    "ic_filter.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M22,3H2l8,9.46V19l4,2v-8.54L22,3z" />
</vector>""",
    "ic_sort.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#00000000" android:strokeColor="#000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M3,6h18M3,12h12M3,18h6" />
</vector>""",
    "bg_search_bar.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#F8F8F0" />
    <corners android:radius="24dp" />
</shape>""",
    "bg_chip.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#F8F8F0" />
    <corners android:radius="16dp" />
    <padding android:left="16dp" android:top="8dp" android:right="16dp" android:bottom="8dp" />
</shape>"""
}

for name, content in drawables.items():
    with open(os.path.join(base_dir, name), "w") as f:
        f.write(content)
print("Drawables created!")
