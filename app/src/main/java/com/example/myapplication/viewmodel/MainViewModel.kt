package com.example.myapplication.viewmodel

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.model.*
import com.example.myapplication.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.awaitResponse

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = ApiService.create()
    private val sessionPreferences = application.getSharedPreferences("fundifix_session", Application.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<AppScreen>(AppScreen.SPLASH)
    val uiState: StateFlow<AppScreen> = _uiState

    private val _userRole = MutableStateFlow<UserRole>(UserRole.CLIENT)
    val userRole: StateFlow<UserRole> = _userRole

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _language = MutableStateFlow(AppLanguage.SWAHILI)
    val language: StateFlow<AppLanguage> = _language

    private val _currentUser = MutableStateFlow<UserData?>(null)
    val currentUser: StateFlow<UserData?> = _currentUser

    private val _availableJobs = MutableStateFlow<List<ServiceReq>>(emptyList())
    val availableJobs: StateFlow<List<ServiceReq>> = _availableJobs

    private val _clientRequests = MutableStateFlow<List<ServiceReq>>(emptyList())
    val clientRequests: StateFlow<List<ServiceReq>> = _clientRequests

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val phone = sessionPreferences.getString("phone", null) ?: return
        val roleName = sessionPreferences.getString("role", UserRole.CLIENT.name) ?: UserRole.CLIENT.name
        val role = runCatching { UserRole.valueOf(roleName) }.getOrDefault(UserRole.CLIENT)
        val user = UserData(
            phone = phone,
            role = role.name,
            method = sessionPreferences.getString("method", "OTP") ?: "OTP",
            name = sessionPreferences.getString("name", null),
            profileImage = sessionPreferences.getString("profileImage", null)
        )
        _userRole.value = role
        _currentUser.value = user
        _uiState.value = AppScreen.DASHBOARD
    }

    private fun saveSession(user: UserData) {
        sessionPreferences.edit()
            .putString("phone", user.phone)
            .putString("role", user.role)
            .putString("method", user.method)
            .putString("name", user.name)
            .putString("profileImage", user.profileImage)
            .apply()
    }

    fun logout() {
        sessionPreferences.edit().clear().apply()
        _currentUser.value = null
        _userRole.value = UserRole.CLIENT
        _uiState.value = AppScreen.ROLE_SELECT
    }

    fun setScreen(screen: AppScreen) {
        _uiState.value = screen
        _errorMessage.value = "" // Clear error on screen change
    }

    fun setRole(role: UserRole) {
        _userRole.value = role
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
    }

    fun updateProfileImage(uri: Uri) {
        // Mock update - In real app, upload to Cloudinary/Firebase
        _currentUser.value = _currentUser.value?.copy(profileImage = uri.toString())?.also { saveSession(it) }
    }

    fun startRegistration(phone: String, name: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = UserData(phone, _userRole.value.name, "SMS", name = name, password = password)
                val response = apiService.register(data).awaitResponse()
                onResult(response.isSuccessful && response.body()?.success == true,
                    response.body()?.message ?: "Imeshindikana kuanza usajili.")
            } catch (e: Exception) {
                onResult(false, "Tatizo la mtandao. Hakiki muunganisho wako.")
            }
        }
    }

    fun completeRegistration(phone: String, name: String, password: String, otp: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.verifyRegistration(OtpVerifyRequest(phone, otp)).awaitResponse()
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = UserData(phone, _userRole.value.name, "SMS", name = name, password = password)
                    _currentUser.value = data
                    saveSession(data)
                    _uiState.value = AppScreen.DASHBOARD
                    onResult(true, "")
                } else {
                    onResult(false, response.body()?.message ?: "OTP sio sahihi.")
                }
            } catch (e: Exception) {
                onResult(false, "Tatizo la mtandao. Hakiki muunganisho wako.")
            }
        }
    }

    fun registerUser(phone: String, method: String, name: String? = null, password: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            try {
                val data = UserData(phone, _userRole.value.name, method, name = name, password = password)
                val response = apiService.register(data).awaitResponse()
                if (response.isSuccessful && response.body()?.success == true) {
                    _currentUser.value = data
                    saveSession(data)
                    _uiState.value = AppScreen.DASHBOARD
                } else {
                    _errorMessage.value = response.body()?.message
                        ?: if (response.code() == 409) "Namba hii tayari imesajiliwa. Tumia Ingia kwa Password."
                        else "Usajili umefeli. Jaribu baadae."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Tatizo la mtandao. Hakiki muunganisho wako."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loginWithPassword(phone: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.login(PasswordLoginRequest(phone, password, _userRole.value.name)).awaitResponse()
                if (response.isSuccessful && response.body()?.phone != null) {
                    val user = response.body()!!
                    val detectedRole = runCatching { UserRole.valueOf(user.role.uppercase()) }
                        .getOrDefault(_userRole.value)
                    _userRole.value = detectedRole
                    _currentUser.value = user
                    saveSession(user)
                    _uiState.value = AppScreen.DASHBOARD
                    onResult(true, "")
                } else {
                    onResult(false, "Namba au password si sahihi.")
                }
            } catch (e: Exception) {
                onResult(false, "Tatizo la mtandao. Hakiki muunganisho wako.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitServiceRequest(service: String, desc: String, phone: String, price: String, date: String?, location: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val req = ServiceReq(service = service, desc = desc, clientPhone = phone, price = price, bookingDate = date, location = location)
                val response = apiService.submitRequest(req).awaitResponse()
                if (response.isSuccessful) {
                    _uiState.value = AppScreen.DASHBOARD
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchJobs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getRequests().awaitResponse()
                if (response.isSuccessful) {
                    _availableJobs.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchClientRequests(phone: String) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            try {
                val response = apiService.getClientRequests(phone).awaitResponse()
                if (response.isSuccessful) {
                    _clientRequests.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Imeshindikana kupata bili zako."
            }
        }
    }

    fun updateJobPrice(jobId: String, price: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.updateRequestPrice(jobId, mapOf("price" to price)).awaitResponse()
                if (response.isSuccessful && response.body()?.success == true) {
                    _availableJobs.value = _availableJobs.value.map { job ->
                        if (job.id == jobId) job.copy(price = price) else job
                    }
                } else {
                    _errorMessage.value = response.body()?.message ?: "Imeshindikana kubadilisha bill."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Imeshindikana kubadilisha bill."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.acceptRequest(jobId).awaitResponse()
                if (response.isSuccessful && response.body()?.success == true) {
                    _availableJobs.value = _availableJobs.value.filter { it.id != jobId }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Imeshindikana kukubali kazi."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun rejectJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.rejectRequest(jobId).awaitResponse()
                if (response.isSuccessful && response.body()?.success == true) {
                    _availableJobs.value = _availableJobs.value.filter { it.id != jobId }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Imeshindikana kukataa kazi."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
