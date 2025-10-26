document.getElementById('signupForm').addEventListener('submit', async function(e) {
    e.preventDefault();

    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value.trim();
    const signupButton = e.target.querySelector('.login-btn');

    if (!name || !email || !password) {
        alert('Please fill out all fields.');
        return;
    }

    signupButton.textContent = 'Creating...';
    signupButton.disabled = true;

    const formData = new URLSearchParams();
    formData.append('name', name);
    formData.append('email', email);
    formData.append('password', password);

    try {
        // This will call the 'RegisterServlet' we are about to create
        const response = await fetch('register', {
            method: 'POST',
            body: formData,
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        });

        const result = await response.json();

        if (response.ok && result.success) {
            alert('Account created successfully! Please log in.');
            window.location.href = "login.html"; // Redirect to login page
        } else {
            alert('Registration Failed: ' + result.message);
        }
    } catch (error) {
        console.error('Signup error:', error);
        alert('An error occurred. Please try again.');
    } finally {
        signupButton.textContent = 'Create Account';
        signupButton.disabled = false;
    }
});