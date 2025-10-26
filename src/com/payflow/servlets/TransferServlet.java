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

import com.google.gson.JsonObject;
import com.payflow.db.DatabaseConnection;

public class TransferServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Not logged in.");
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        int senderAccountId = (int) session.getAttribute("account_id");
        String recipientWalletId = request.getParameter("recipientWalletId");
        double amount = Double.parseDouble(request.getParameter("amount"));
        String note = request.getParameter("note");

        Connection conn = DatabaseConnection.getConnection();

        try {
            // --- Start Transaction ---
            conn.setAutoCommit(false);

            // 1. Get sender's current balance and check funds
            double senderBalance = 0;
            String balanceSql = "SELECT balance FROM accounts WHERE account_id = ? FOR UPDATE";
            try (PreparedStatement stmt = conn.prepareStatement(balanceSql)) {
                stmt.setInt(1, senderAccountId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    senderBalance = rs.getDouble("balance");
                }
            }

            if (senderBalance < amount) {
                conn.rollback();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Insufficient balance!");
                out.print(jsonResponse.toString());
                out.flush();
                return;
            }

            // 2. Get receiver's account_id
            int receiverAccountId = -1;
            String receiverSql = "SELECT account_id FROM accounts WHERE wallet_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(receiverSql)) {
                stmt.setString(1, recipientWalletId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    receiverAccountId = rs.getInt("account_id");
                }
            }

            if (receiverAccountId == -1) {
                conn.rollback();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Recipient wallet ID not found.");
                out.print(jsonResponse.toString());
                out.flush();
                return;
            }

            if(receiverAccountId == senderAccountId) {
                conn.rollback();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "You cannot send money to yourself.");
                out.print(jsonResponse.toString());
                out.flush();
                return;
            }

            // 3. Decrement sender's balance
            String updateSenderSql = "UPDATE accounts SET balance = balance - ? WHERE account_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSenderSql)) {
                stmt.setDouble(1, amount);
                stmt.setInt(2, senderAccountId);
                stmt.executeUpdate();
            }

            // 4. Increment receiver's balance
            String updateReceiverSql = "UPDATE accounts SET balance = balance + ? WHERE account_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateReceiverSql)) {
                stmt.setDouble(1, amount);
                stmt.setInt(2, receiverAccountId);
                stmt.executeUpdate();
            }

            // 5. Create transaction record
            String insertTxSql = "INSERT INTO transactions (sender_account_id, receiver_account_id, amount, transaction_type, note) " +
                    "VALUES (?, ?, ?, 'transfer', ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertTxSql)) {
                stmt.setInt(1, senderAccountId);
                stmt.setInt(2, receiverAccountId);
                stmt.setDouble(3, amount);
                stmt.setString(4, note);
                stmt.executeUpdate();
            }

            // --- Commit Transaction ---
            conn.commit();
            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Money sent successfully!");

        } catch (SQLException e) {
            try {
                conn.rollback(); // Rollback on error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "A database error occurred during the transfer.");
        } finally {
            try {
                conn.setAutoCommit(true); // Reset to default
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        out.print(jsonResponse.toString());
        out.flush();
    }
}