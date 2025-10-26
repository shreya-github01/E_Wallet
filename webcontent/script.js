// Global state variables
let transactions = [];
let balance = 0;

// --- DATA FETCHING & DISPLAY ---

// Fetches all dashboard data from the servlet
async function loadDashboardData() {
    try {
        const response = await fetch('dashboard-data'); // Calls DashboardServlet

        if (!response.ok) {
            // If not logged in (401), redirect to login
            if (response.status === 401) {
                alert('You are not logged in. Redirecting to login page.');
                window.location.href = 'login.html';
            }
            throw new Error('Failed to load data.');
        }

        const data = await response.json();

        if (data.success) {
            // Update global state
            balance = data.balance;
            transactions = data.transactions;

            // Update UI elements
            document.getElementById('balanceDisplay').textContent = `₹${balance.toFixed(2)}`;
            document.querySelector('.wallet-id').textContent = `Wallet ID: ${data.walletId}`;

            // Update header
            document.getElementById('userNameDisplay').textContent = data.userName;

            // Update sidebar header
            document.getElementById('sidebarUserName').textContent = data.userName;
            document.getElementById('sidebarUserEmail').textContent = data.userEmail;

            // Update sidebar info section
            document.getElementById('sidebarInfoName').textContent = data.userName;
            document.getElementById('sidebarInfoMobile').textContent = data.mobile || 'Not set';
            document.getElementById('sidebarInfoAddress').textContent = data.address || 'Not set';

            displayTransactions();
        } else {
            alert('Error: ' + data.message);
        }
    } catch (error) {
        console.error('Failed to load dashboard data:', error);
    }
}

function displayTransactions(limit = 4) {
    const transactionList = document.getElementById('transactionList');

    if (transactions.length === 0) {
        transactionList.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📭</div>
                <div>No transactions yet</div>
            </div>
        `;
        return;
    }

    const displayList = transactions.slice(0, limit);
    transactionList.innerHTML = displayList.map(t => `
        <div class="transaction-item">
            <div class="transaction-icon ${t.type}">
                ${t.type === 'sent' ? '↑' : '↓'}
            </div>
            <div class="transaction-details">
                <div class="transaction-name">${t.name}</div>
                <div class="transaction-date">${formatDate(t.date)} • ${t.note || ''}</div>
            </div>
            <div class="transaction-amount ${t.type}">
                ${t.type === 'sent' ? '-' : '+'}₹${t.amount.toFixed(2)}
            </div>
        </div>
    `).join('');
}

function formatDate(dateStr) {
    const date = new Date(dateStr);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    if (date.toDateString() === today.toDateString()) {
        return 'Today';
    } else if (date.toDateString() === yesterday.toDateString()) {
        return 'Yesterday';
    } else {
        return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    }
}

// --- MODAL HANDLING ---

function openModal(type) {
    document.getElementById(type + 'Modal').classList.add('active');
}

function closeModal(type) {
    document.getElementById(type + 'Modal').classList.remove('active');
}

// Close modal if user clicks outside of modal-content
window.onclick = function(event) {
    if (event.target.classList.contains('modal')) {
        event.target.classList.remove('active');
    }
}

// --- SIDEBAR HANDLING ---
function toggleProfileSidebar() {
    document.getElementById('profileSidebar').classList.toggle('active');
    document.getElementById('sidebarOverlay').classList.toggle('active');
}

// --- LOGOUT FUNCTION ---
async function logout() {
    toggleProfileSidebar(); // Close sidebar
    await fetch('logout');
    alert("You have been logged out.");
    window.location.href = 'login.html'; // Redirect to login
}

// --- PROFILE MODAL: LOAD DATA ---
async function openProfileModal() {
    try {
        const response = await fetch('profile'); // Calls ProfileServlet GET

        if (!response.ok) {
            throw new Error('Could not load profile. Please log in again.');
        }

        const data = await response.json();

        if (data.success) {
            // Populate the modal fields
            document.getElementById('profileName').value = data.name;
            document.getElementById('profileEmail').value = data.email;
            document.getElementById('profileMobile').value = data.mobile || '';
            document.getElementById('profileAddress').value = data.address || '';

            toggleProfileSidebar(); // Close sidebar
            openModal('profile');   // Open modal
        } else {
            alert('Error: ' + data.message);
        }
    } catch (error) {
        alert(error.message);
        window.location.href = 'login.html'; // Force re-login
    }
}

// --- PROFILE MODAL: SAVE UPDATES ---
async function handleProfileUpdate(event) {
    event.preventDefault();
    const form = event.target;
    const name = document.getElementById('profileName').value;
    const mobile = document.getElementById('profileMobile').value;
    const address = document.getElementById('profileAddress').value;
    const submitButton = form.querySelector('.submit-btn');

    submitButton.textContent = 'Saving...';
    submitButton.disabled = true;

    const formData = new URLSearchParams();
    formData.append('name', name);
    formData.append('mobile', mobile);
    formData.append('address', address);

    try {
        const response = await fetch('profile', { // Calls ProfileServlet POST
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (response.ok && result.success) {
            alert('Profile updated successfully!');
            closeModal('profile');
            loadDashboardData(); // Refresh all data (header, sidebar, etc.)
        } else {
            alert('Update failed: ' + result.message);
        }
    } catch (error) {
        alert('An error occurred: ' + error.message);
    } finally {
        submitButton.textContent = 'Save Changes';
        submitButton.disabled = false;
    }
}


// --- FORM SUBMISSIONS ---

async function handleTransfer(event) {
    event.preventDefault();
    const form = event.target;
    const recipientWalletId = form.querySelector('input[type="text"]').value;
    const amount = parseFloat(form.querySelector('input[type="number"]').value);
    const note = form.querySelectorAll('input[type="text"]')[1].value || 'Transfer';
    const submitButton = form.querySelector('.submit-btn');

    if (amount > balance) {
        alert('Insufficient balance!');
        return;
    }

    submitButton.textContent = 'Sending...';
    submitButton.disabled = true;

    const formData = new URLSearchParams();
    formData.append('recipientWalletId', recipientWalletId);
    formData.append('amount', amount);
    formData.append('note', note);

    try {
        const response = await fetch('transfer', { // Calls TransferServlet
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (response.ok && result.success) {
            alert('Money sent successfully!');
            closeModal('transfer');
            form.reset();
            loadDashboardData(); // Refresh all data
        } else {
            alert('Transfer Failed: ' + result.message);
        }
    } catch (error) {
        console.error('Transfer error:', error);
        alert('An error occurred during the transfer.');
    } finally {
        submitButton.textContent = 'Send Money';
        submitButton.disabled = false;
    }
}

// (This is left as a simple alert for now)
function handleRequest(event) {
    event.preventDefault();
    const form = event.target;
    closeModal('receive');
    form.reset();
    alert('Money request sent successfully! (Frontend Only)');
}

async function handleAddFunds(event) {
    event.preventDefault();
    const form = event.target;
    const amount = parseFloat(form.querySelector('input[type="number"]').value);
    const paymentMethod = form.querySelector('select').value;
    const submitButton = form.querySelector('.submit-btn');

    if (amount <= 0 || !paymentMethod) {
        alert('Please enter a valid amount and payment method.');
        return;
    }

    submitButton.textContent = 'Adding...';
    submitButton.disabled = true;

    const formData = new URLSearchParams();
    formData.append('amount', amount);
    formData.append('paymentMethod', paymentMethod);

    try {
        const response = await fetch('add-funds', { // Calls AddFundsServlet
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (response.ok && result.success) {
            alert('Funds added successfully!');
            closeModal('addFunds');
            form.reset();
            loadDashboardData(); // Refresh all data (balance and transactions)
        } else {
            alert('Failed to add funds: ' + result.message);
        }
    } catch (error) {
        console.error('Add funds error:', error);
        alert('An error occurred while adding funds.');
    } finally {
        submitButton.textContent = 'Add Funds';
        submitButton.disabled = false;
    }
}

function showHistory() {
    displayTransactions(transactions.length); // Show all loaded transactions
}

// --- INITIALIZATION ---
// Load all data when the page is ready
document.addEventListener('DOMContentLoaded', loadDashboardData);