package com.nexaleads.app.ui.viewmodel

import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.Order
import com.nexaleads.app.data.repository.LeadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.content.Context

@OptIn(ExperimentalCoroutinesApi::class)
class CallingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: LeadRepository
    private lateinit var context: Context
    private lateinit var viewModel: CallingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        viewModel = CallingViewModel(repository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateLead with Order Placed status syncs order fields to repository`() = runTest {
        // Arrange
        val leadId = "lead-1"
        val orderId = "order-1"
        
        // Mock Lead with updated address and amount
        val editedLead = Lead(
            id = leadId,
            status = Constants.STATUS_ORDER_PLACED,
            address = "New Address, Mumbai",
            orderAmount = "₹2500"
        )
        
        // Mock the latest order in the database
        val latestOrder = Order(
            id = orderId,
            customerId = leadId,
            status = Constants.STATUS_ORDER_PLACED,
            orderAmount = "₹1500"
        )
        
        coEvery { repository.getLatestOrderForCustomer(leadId) } returns latestOrder
        coEvery { 
            repository.updateLeadAndAddInteractionBatch(any(), any(), any(), any(), any()) 
        } returns Unit

        // Act
        viewModel.updateLead(editedLead)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) {
            repository.updateLeadAndAddInteractionBatch(
                leadId = eq(leadId),
                updates = any(),
                interaction = any(),
                orderId = eq(orderId),
                orderUpdates = match { updates ->
                    updates != null &&
                    updates["orderAmount"] == "₹2500" &&
                    updates["orderAmountNum"] == 2500L
                }
            )
        }
    }
    
    @Test
    fun `updateLead with Non-Order status does not sync order fields`() = runTest {
        // Arrange
        val leadId = "lead-2"
        
        val editedLead = Lead(
            id = leadId,
            status = Constants.STATUS_FOLLOW_UP,
            address = "Random Edit"
        )
        
        // Act
        viewModel.updateLead(editedLead)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) {
            repository.updateLeadAndAddInteractionBatch(
                leadId = eq(leadId),
                updates = any(),
                interaction = any(),
                orderId = isNull(), // Should be null because status != Order Placed
                orderUpdates = isNull()
            )
        }
        
        // Should not even try to fetch the order
        coVerify(exactly = 0) { repository.getLatestOrderForCustomer(any()) }
    }
}
