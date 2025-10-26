package com.payflow.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.payflow.db.DatabaseConnection;

public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false); // Do not create a new session
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Not logged in. Please log in first.");
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        int userId = (int) session.getAttribute("user_id");
        String userName = (String) session.getAttribute("name");
        String userEmail = (String) session.getAttribute("email"); // <-- THIS LINE IS ADDED
        Connection conn = DatabaseConnection.getConnection();

        try {
            // --- 1. Get Account Details (Balance, Wallet ID) ---
            int accountId = -1;
            String walletId = "";
            double balance = 0;

            String accountSql = "SELECT account_id, wallet_id, balance FROM accounts WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(accountSql)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    accountId = rs.getInt("account_id");
                    walletId = rs.getString("wallet_id");
                    balance = rs.getDouble("balance");

                    // Add account info to session for other servlets
                    session.setAttribute("account_id", accountId);
                }
            }

            // --- 2. Get Transaction History ---
            JsonArray transactionsArray = new JsonArray();
            // (Transaction query remains the same)
            String transSql = "SELECT t.*, " +
                    " (SELECT u.name FROM accounts a JOIN users u ON a.user_id = u.user_id WHERE a.account_id = t.sender_account_id) AS sender_name, " +
                    " (SELECT u.name FROM accounts a JOIN users u ON a.user_id = u.user_id WHERE a.account_id = t.receiver_account_id) AS receiver_name " +
                    " FROM transactions t " +
                    " WHERE t.sender_account_id = ? OR t.receiver_account_id = ? " +
                    " ORDER BY t.transaction_date DESC";

            try (PreparedStatement stmt = conn.prepareStatement(transSql)) {
                stmt.setInt(1, accountId);
                stmt.setInt(2, accountId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    JsonObject tx = new JsonObject();
                    String type = rs.getString("transaction_type");
                    int senderId = rs.getInt("sender_account_id");

                    if (type.equals("add_funds")) {
                        tx.addProperty("type", "received");
                        tx.addProperty("name", "Added Funds");
                    } else if (senderId == accountId) {
                        tx.addProperty("type", "sent");
                        tx.addProperty("name", rs.getString("receiver_name"));
                    } else {
                        tx.addProperty("type", "received");
                        tx.addProperty("name", rs.getString("sender_name"));
                    }

                    tx.addProperty("id", rs.getInt("transaction_id"));
                    tx.addProperty("amount", rs.getDouble("amount"));
                    tx.addProperty("date", rs.getTimestamp("transaction_date").toString());
                    tx.addProperty("note", rs.getString("note"));
                    transactionsArray.add(tx);
                }
            }

            // --- 3. Build Final JSON Response ---
            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("userName", userName);
            jsonResponse.addProperty("userEmail", userEmail); // <-- THIS LINE IS ADDED
            jsonResponse.addProperty("walletId", walletId);
            jsonResponse.addProperty("balance", balance);
            jsonResponse.add("transactions", transactionsArray);

            out.print(jsonResponse.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Database error.");
            out.print(jsonResponse.toString());
        }

        out.flush();
    }
}