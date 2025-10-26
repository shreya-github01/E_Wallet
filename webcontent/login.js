function togglePassword() {
    const passwordInput = document.getElementById('password');
    const icon = event.target;

    if (passwordInput.type === 'password') {
        passwordInput.type = 'text';
        icon.textContent = '🙈';
    } else {
        passwordInput.type = 'password';
        icon.textContent = '👁️';
    }
}

document.getElementById('loginForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value.trim();
    const loginButton = e.target.querySelector('.login-btn');

    if (!email || !password) {
        alert('Please enter both email and password.');
        return;
    }

    loginButton.textContent = 'Logging in...';
    loginButton.disabled = true;

    // Use FormData to send data to the servlet
    const formData = new URLSearchParams();
    formData.append('email', email);
    formData.append('password', password);

    try {
        // The URL path must match your project name on Tomcat + servlet mapping
        // e.g., /E-wallet/login
        const response = await fetch('login', {
            method: 'POST',
            body: formData,
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        });

        const result = await response.json();

        if (response.ok && result.success) {
            alert('Login successful! Redirecting...');
            window.location.href = "index.html"; // Redirect to dashboard
        } else {
            alert('Login Failed: ' + result.message);
        }
    } catch (error) {
        console.error('Login error:', error);
        alert('An error occurred. Please try again.');
    } finally {
        loginButton.textContent = 'Login';
        loginButton.disabled = false;
    }
});
