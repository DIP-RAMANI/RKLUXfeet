# RKLUXfeet - Application Flow & Documentation

RKLUXfeet is a premium e-commerce Android application designed for high-end footwear. This document provides a comprehensive walkthrough of the application's user journey, features, and technical architecture.

---

## 🚀 1. Entry & Authentication Flow

### **A. Splash / Entry Screen (MainActivity)**
- **First Impression**: The app opens with a smooth animation of the logo and branding.
- **Entry Points**: 
  - **Login**: For existing users.
  - **Register**: For new users to create an account.
- **Auto-Login**: If a user is already authenticated via Firebase, the app automatically skips this screen and redirects to the **Home** dashboard.

### **B. Registration (RegisterActivity)**
- **Fields**: Full Name, Email, Password, and Confirm Password.
- **Security**: Features a real-time **Password Strength Indicator** using colored bars (Red/Orange/Green).
- **Social Login**: Integrated with **Google Sign-In** for quick account creation.
- **Backend**: Captures user data and stores it in **Firebase Firestore** under the `users` collection.

### **C. Login (LoginActivity)**
- Supports traditional Email/Password authentication.
- **One-Tap Login**: Google Sign-In integration.
- **Recovery**: Link to "Forgot Password" flow.

### **D. Password Recovery (Forgot/Set New/Success)**
- A multi-step flow to reset passwords via email, concluding with a "Password Updated" success confirmation screen.

---

## 🛍️ 2. The Shopping Experience

### **A. Home Dashboard (HomeActivity)**
- **Dynamic Banners**: An auto-sliding carousel (ViewPager2) showcasing latest offers and brand highlights.
- **Personalized Greeting**: Displays the user's name fetched from Firebase.
- **New Arrivals**: A horizontal list of the latest shoes added to the catalog.
- **Bestsellers**: Displays popular items with unique pastel-colored backgrounds for visual variety.
- **Cart Access**: Quick-access cart icon with a real-time badge showing item count.

### **B. The Store (StoreActivity)**
- **Global Search**: Search for shoes by name.
- **Category Chips**: Filter by All, Price (Low to High), Newest, and Bestsellers.
- **Brand Filters**: Quick filters for major brands (Nike, Adidas, Puma, etc.).
- **Grid Layout**: Displays products in a 2-column responsive grid with ratings and sales counts.

### **C. Product Details (ProductDetailsActivity)**
- **Immersive Visuals**: High-quality images with zoom/center-crop via Glide.
- **Size Selector**: Real-time selection of shoe sizes (UK 6 to UK 10).
- **Dynamic Pricing**: Shows current price vs. an original "strikethrough" price to highlight discounts.
- **Wishlist**: Toggle-able heart icon to save items for later.
- **Interaction**: "Add to Cart" and "Buy Now" (direct to checkout) buttons.

---

## 🛒 3. Cart & Checkout Flow

### **A. Shopping Cart (CartActivity)**
- List of all added items with their selected sizes.
- **Quantity Control**: Increment or decrement items directly in the cart.
- **Summary**: Real-time calculation of Subtotal, Taxes, and Delivery charges.

### **B. Checkout (CheckoutActivity)**
- **Address Management**: Select from saved addresses or add a new one.
- **Billing Summary**: Final breakdown of costs.
- **Payment Gateway**: Integrated flow to complete the purchase (e.g., Razorpay/Firebase status updates).
- **Order Placement**: Moves items from `carts` to an `orders` collection in Firestore.

---

## 👤 4. User Profile & Account

### **A. Profile Screen (ProfileActivity)**
- Displays user profile picture and name.
- **Navigation Menu**:
  - **Personal Information**: Edit name and email.
  - **Orders**: View past order history.
  - **Addresses**: Manage multiple shipping locations.
  - **Logout**: Securely exit the application.

### **B. Order History & Details**
- **History**: A chronological list of all previous orders.
- **Detail View**: Full breakdown of a specific order (items, prices, status).
- **Invoices**: Ability to download or share a PDF invoice for each order.

---

## 🛠️ 5. Admin Panel (For Staff)

A dedicated section accessible to admin users to manage the store:
- **Dashboard**: Overview of business metrics.
- **Product Management**: Add, Edit, or Remove shoe listings.
- **User Management**: View and manage the customer base.
- **Order Management**: Track and update order statuses (Pending, Shipped, Delivered).

---

## 🏗️ Technical Architecture

- **Language**: Kotlin
- **Database/Auth**: Firebase (Authentication & Firestore)
- **Image Hosting**: Cloudinary (for product images).
- **UI Architecture**: XML Layouts with Material Design components.
- **Library Highlights**:
  - `Glide`: Image loading and caching.
  - `ChipNavigationBar`: Premium bottom navigation bar.
  - `ViewPager2`: For high-performance banner sliders.
  - `Firebase SDK`: For real-time data syncing.

---

*This document summarizes the core logic and flow of the RKLUXfeet application as of the current build.*
