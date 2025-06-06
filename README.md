# TeamSync App

TeamSync is a modern Android application built with Jetpack Compose, designed to facilitate real-time location sharing, group communication, and team coordination. It provides a robust platform for managing groups, tracking members, and analyzing travel history, making it ideal for families, friends, or small teams.

## Table of Contents

* [Features](#features)
* [Technologies Used](#technologies-used)
* [Project Structure](#project-structure)
* [Setup Instructions](#setup-instructions)
* [Usage](#usage)
* [Planned Future Enhancements](#planned-future-enhancements)

## Features

TeamSync offers a comprehensive suite of features for group management and location-based services:

* **User Authentication**: Secure user login and signup with email/password, integrated with Firebase Authentication. Includes robust error handling for various authentication scenarios.
* **User Profiles**: Create and manage personal profiles including name, display name, city, state, date of birth, and profile photos.
* **Group Management**:
    * **Group Creation**: Users can create new groups with customizable names, descriptions, access codes, and optional passwords.
    * **Plan Selection**: Choose between "Freemium" and "Basic Paid" plans, offering different limits on members, location update intervals, and feature availability (e.g., private chats, photo sharing, location history, dispatch mode).
    * **Join Group**: Easily join existing groups using an access code and password.
    * **Groups List**: View and manage all active groups a user is a member of. Switch between active groups or leave a group.
    * **Owner Responsibilities**: Group owners have the option to "shut down" a group, which permanently deletes all group data.
* **Real-time Location Tracking**:
    * **Live Map Display**: View the real-time locations of yourself and other active group members on a Google Map interface.
    * **Custom Markers**: User locations are represented by custom markers displaying profile pictures, with optional bearing indicators for photo markers.
    * **Configurable Intervals**: Location updates can be configured based on group settings and personal overrides, from real-time to every 5 minutes.
    * **Background Tracking**: Utilizes a foreground service to ensure continuous location tracking even when the app is in the background.
* **Map Markers**:
    * **Add Markers**: Users can add two types of markers to the map: chat markers (text messages) and photo markers (geotagged images).
    * **Photo Geotagging**: Photo markers capture the device's location and camera bearing at the time of posting.
    * **Interactive Info Dialogs**: Tapping on a marker displays detailed information, including location, timestamp, speed, bearing, and address (via geocoding).
* **Team List**: View a detailed list of all active members within the current group, including their last known location, update time, speed, and direction. Addresses for members are dynamically geocoded.
* **Travel Reports**: Generate travel reports for individual users, segmenting their location history into stationary periods, travel segments, and data gaps over the last 24 hours. Provides details like duration, distance, average speed, and addresses for stationary points.
* **Battery Information**: Displays the battery level and charging status of group members.
* **Unit Conversion**: Helper functions for converting units like meters per second to miles per hour, and bearing to cardinal directions.
* **Robust Permissions Handling**: Manages Android runtime permissions for location, camera, storage, and notifications with clear rationale and user guidance.
* **Firebase Integration**: Leverages Firebase for backend services, including:
    * **Firestore**: For storing and syncing user profiles, group data, group memberships, location history, current user locations, and map markers.
    * **Firebase Storage**: For uploading and retrieving user profile photos and photo marker images.
* **Jetpack Compose UI**: A modern, declarative UI built entirely with Jetpack Compose for a responsive and visually appealing user experience.

## Technologies Used

* **Kotlin**: Primary programming language.
* **Jetpack Compose**: For building the native Android UI.
* **Firebase**:
    * **Firebase Authentication**: User sign-up and sign-in.
    * **Firestore**: NoSQL cloud database for real-time data synchronization.
    * **Firebase Storage**: Cloud storage for user-generated content (e.g., profile pictures, photo markers).
* **Google Maps Platform**:
    * **Maps SDK for Android (Compose)**: Integrating Google Maps into the Compose UI.
    * **Google Location Services (FusedLocationProviderClient)**: For efficient and accurate location retrieval.
    * **Geocoder**: For converting geographical coordinates into human-readable addresses.
* **Coil**: An image loading library for Android, used for displaying profile pictures and photo markers.
* **UCrop**: A powerful image cropping library.
* **AndroidX Libraries**: A collection of libraries that help develop high-quality, robust, and compatible apps.
* **Gradle Kotlin DSL**: For build configuration.

## Project Structure

The project follows a standard Android project structure with a modularized approach:
