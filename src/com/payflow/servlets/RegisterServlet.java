package com.payflow.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;
import com.payflow.db.DatabaseConnection;

public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String password = request.getParameter("password"); // Remember to hash this in a real app!

        Connection conn = DatabaseConnection.getConnection();
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        try {
            // --- Start Database Transaction ---
            // We are creating two records (user and account),
            // so we must use a transaction.
            conn.setAutoCommit(false);

            // 1. Check if user email already exists
            String checkUserSql = "SELECT user_id FROM users WHERE email = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {
                checkStmt.setString(1, email);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    conn.rollback(); // Cancel the transaction
                    jsonResponse.addProperty("success", false);
                    jsonResponse.addProperty("message", "An account with this email already exists.");
                    response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
                    out.print(jsonResponse.toString());
                    out.flush();
                    return;
                }
            }

            // 2. Insert new user
            String insertUserSql = "INSERT INTO users (name, email, password) VALUES (?, ?, ?)";
            int newUserId = -1;
            try (PreparedStatement insertStmt = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, name);
                insertStmt.setString(2, email);
                insertStmt.setString(3, password);
                insertStmt.executeUpdate();

                // Get the newly generated user_id
                ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    newUserId = generatedKeys.getInt(1);
                }
            }

            if (newUserId == -1) {
                conn.rollback();
                throw new SQLException("Failed to create user, no ID obtained.");
            }

            // 3. Create a new wallet account for this user
            String walletId = generateWalletId();
            String insertAccountSql = "INSERT INTO accounts (user_id, wallet_id, balance) VALUES (?, ?, 0.00)";
            try (PreparedStatement insertAcctStmt = conn.prepareStatement(insertAccountSql)) {
                insertAcctStmt.setInt(1, newUserId);
                insertAcctStmt.setString(2, walletId);
                insertAcctStmt.executeUpdate();
            }

            // 4. If all steps succeeded, commit the transaction
            conn.commit();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Account created successfully!");
            out.print(jsonResponse.toString());

        } catch (SQLException e) {
            try {
                conn.rollback(); // Rollback on any error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "A database error occurred.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(jsonResponse.toString());

        } finally {
            try {
                conn.setAutoCommit(true); // Always reset auto-commit
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        out.flush();
    }

    // Helper method to generate a random wallet ID
    private String generateWalletId() {
        Random rand = new Random();
        int num = 10000000 + rand.nextInt(90000000); // 8-digit number
        return "WLT-" + num;
    }
}