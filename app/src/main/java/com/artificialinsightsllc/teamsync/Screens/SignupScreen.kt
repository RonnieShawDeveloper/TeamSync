package com.artificialinsightsllc.teamsync.Screens.Signup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState // Import this
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.navigationBarsPadding
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(navController: NavHostController) {
    val context = LocalContext.current
    val darkBlue = Color(0xFF0D47A1)
    val formatter = SimpleDateFormat("MMddyyyy", Locale.US)

    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var firstName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var croppedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptionDialog by remember { mutableStateOf(false) }
    var tempPhotoUri: Uri? by remember { mutableStateOf(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Date Picker states
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val cropResultLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            croppedImageUri = resultUri
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let {
            val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_image.jpg"))
            val intent = UCrop.of(it, destinationUri)
                .withAspectRatio(1f, 1f)
                .getIntent(context)
            cropResultLauncher.launch(intent)
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success && tempPhotoUri != null) {
            val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_image.jpg"))
            val options = UCrop.Options().apply {
                setCircleDimmedLayer(true)
                setShowCropFrame(false)
                setShowCropGrid(false)
                setToolbarTitle("Crop Profile Photo")
                setToolbarColor(0xFF0D47A1.toInt())
                setStatusBarColor(0xFF0D47A1.toInt())
                setToolbarWidgetColor(0xFFFFFFFF.toInt())
            }

            val intent = UCrop.of(tempPhotoUri!!, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(512, 512)
                .withOptions(options)
                .getIntent(context)
            cropResultLauncher.launch(intent)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            val photoFile = File(context.cacheDir, "photo.jpg")
            tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
            takePhotoLauncher.launch(tempPhotoUri!!)
        }
    }

    fun isEmailValid(email: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    fun capitalizeWords(input: String): String {
        return input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
    }

    fun validateFields(): Boolean {
        return firstName.isNotBlank() && lastName.isNotBlank() && displayName.isNotBlank() &&
                city.isNotBlank() && state.isNotBlank() && dob.isNotBlank() &&
                email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank() &&
                isEmailValid(email) && password == confirmPassword
    }

    fun createUser() {
        if (!validateFields()) {
            Toast.makeText(context, "Please fill all required fields and ensure email/password are valid.", Toast.LENGTH_LONG).show()
            return
        }

        isLoading = true

        auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener { authResult ->
            val userId = authResult.user?.uid ?: return@addOnSuccessListener

            fun saveUserToFirestore(photoUrl: String) {
                val user = UserModel(
                    authId = userId,
                    userId = userId,
                    firstName = capitalizeWords(firstName),
                    middleName = capitalizeWords(middleName),
                    lastName = capitalizeWords(lastName),
                    displayName = capitalizeWords(displayName),
                    city = capitalizeWords(city),
                    state = capitalizeWords(state),
                    dateOfBirth = dob,
                    profilePhotoUrl = photoUrl,
                    email = email.trim(),
                    profileComplete = true
                )
                firestore.collection("users").document(userId).set(user)
                    .addOnSuccessListener {
                        isLoading = false
                        navController.navigate(NavRoutes.PRE_CHECK) {
                            popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        }
                    }
                    .addOnFailureListener {
                        isLoading = false
                        Toast.makeText(context, "Failed to save user.", Toast.LENGTH_SHORT).show()
                    }
            }

            if (croppedImageUri != null) {
                val ref = storage.reference.child("profilePhotos/$userId.jpg")
                ref.putFile(croppedImageUri!!)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                        ref.downloadUrl
                    }
                    .addOnSuccessListener { uri -> saveUserToFirestore(uri.toString()) }
                    .addOnFailureListener {
                        isLoading = false
                        Toast.makeText(context, "Photo upload failed", Toast.LENGTH_SHORT).show()
                    }
            } else {
                saveUserToFirestore("")
            }

        }.addOnFailureListener {
            isLoading = false
            Toast.makeText(context, "Error: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    if (showPhotoOptionDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionDialog = false },
            title = { Text("Set Profile Photo") },
            text = { Text("Would you like to take a new photo or select one from your gallery?") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoOptionDialog = false
                    val permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (permissionGranted) {
                        val photoFile = File(context.cacheDir, "photo.jpg")
                        tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                        takePhotoLauncher.launch(tempPhotoUri!!)
                    } else {
                        showPermissionDialog = true
                    }
                }) {
                    Text("Take New Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoOptionDialog = false
                    imagePickerLauncher.launch("image/*")
                }) {
                    Text("Upload from Gallery")
                }
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Camera Permission") },
            text = { Text("We need access to your camera so you can take a profile picture.") }
        )
    }

    // Date Picker Dialog
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDateMillis = datePickerState.selectedDateMillis
                    if (selectedDateMillis != null) {
                        val date = Date(selectedDateMillis)
                        dob = formatter.format(date)
                    }
                    showDatePickerDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            croppedImageUri?.let {
                Image(
                    bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it)).asImageBitmap(),
                    contentDescription = "Profile Photo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .shadow(10.dp, CircleShape, clip = false)
                        .clickable { showPhotoOptionDialog = true },
                    contentScale = ContentScale.Crop
                )
                Button(
                    onClick = { showPhotoOptionDialog = true },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Edit Profile Photo")
                }
            } ?: Button(
                onClick = { showPhotoOptionDialog = true },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Please Set a Profile Photo")
            }

            InputField("First Name", firstName, onChange = { firstName = it }, textColor = darkBlue)
            InputField("Middle Name", middleName, onChange = { middleName = it }, textColor = darkBlue)
            InputField("Last Name", lastName, onChange = { lastName = it }, textColor = darkBlue)
            InputField("Display Name", displayName, onChange = { displayName = it }, textColor = darkBlue)
            InputField("City", city, onChange = { city = it }, textColor = darkBlue)
            InputField("State", state, onChange = { state = it }, textColor = darkBlue)

            // MODIFIED DOB Field with focusable = false and explicit interactionSource
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            // Optional: Visual feedback for debugging or better UX
            LaunchedEffect(isPressed) {

            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource, // Pass the interactionSource here
                        indication = null, // Optional: Set to null to remove default ripple if you custom handle it
                        onClick = {
                            showDatePickerDialog = true
                        }
                    )
            ) {
                OutlinedTextField(
                    value = dob,
                    onValueChange = { }, // On value change is empty because the value is set by the date picker
                    label = { Text("Date of Birth (MMDDYYYY)", color = darkBlue) },
                    modifier = Modifier
                        .fillMaxWidth(),
                    readOnly = true, // Keep it read-only so users can't type manually
                    enabled = false, // Disable it to prevent it from getting focus or intercepting clicks
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = darkBlue,
                        unfocusedTextColor = darkBlue,
                        disabledTextColor = darkBlue,
                        focusedBorderColor = darkBlue,
                        unfocusedBorderColor = darkBlue,
                        cursorColor = darkBlue,
                        disabledBorderColor = darkBlue, // Ensure disabled state matches your theme
                        disabledLabelColor = darkBlue,
                        disabledLeadingIconColor = darkBlue,
                        disabledTrailingIconColor = darkBlue
                    )
                )
            }


            Spacer(modifier = Modifier.height(12.dp))
            InputField("Email", email, onChange = { email = it }, textColor = darkBlue)
            Text("A valid email is required and must be verified.", color = Color.Red, fontSize = 12.sp)
            InputField("Password", password, onChange = { password = it }, isPassword = true, textColor = darkBlue)
            InputField("Confirm Password", confirmPassword, onChange = { confirmPassword = it }, isPassword = true, textColor = darkBlue)

            Button(
                onClick = { createUser() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text(if (isLoading) "Creating..." else "Create Account", color = Color.White)
            }
        }
    }
}

@Composable
fun InputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isPassword: Boolean = false,
    textColor: Color = Color.Black
) {
    val darkBlue = Color(0xFF0D47A1)

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = darkBlue) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            disabledTextColor = textColor,
            focusedBorderColor = darkBlue,
            unfocusedBorderColor = darkBlue,
            cursorColor = darkBlue
        )
    )
    Spacer(modifier = Modifier.height(12.dp))
}