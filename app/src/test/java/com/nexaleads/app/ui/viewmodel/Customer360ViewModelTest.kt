package com.nexaleads.app.ui.viewmodel

import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.Order
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.repository.LeadRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 360 Degree TDD Script for Customer360ViewModel
 * 
 * Verifies:
 * 1. Duplicate leads with same phone number are fetched and LTV is calculated securely.
 * 2. LTV completely ignores Canceled, RTO, and Returned orders.
 * 3. Total orders include all history (for UI), but LTV represents only real revenue.
 * 4. Context lead defaults to the parent (most active) lead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Customer360ViewModelTest {

    private lateinit var viewModel: Customer360ViewModel
    private val repository: LeadRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = Customer360ViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchCustomerData calculates accurate LTV excluding RTO and Cancelled orders`() = runTest {
        // Arrange: Mock user phone
        val phone = "+919999999999"

        // Create duplicate leads for the same customer (e.g. from 2 separate campaigns)
        val lead1 = Lead(id = "lead_1", phone = phone, name = "Ramesh (FB)", totalOrdersCount = 1)
        val lead2 = Lead(id = "lead_2", phone = phone, name = "Ramesh (Insta)", totalOrdersCount = 0)
        
        // Setup mock response for leads
        coEvery { repository.getCustomerLeads(phone) } returns listOf(lead1, lead2)
        coEvery { repository.getCustomerInteractions(any()) } returns emptyList()

        // Create 4 orders spanning multiple statuses
        val order1 = Order(id = "o1", orderAmountNum = 1000L, status = "Delivered")
        val order2 = Order(id = "o2", orderAmountNum = 1500L, status = "Order Placed") 
        val order3 = Order(id = "o3", orderAmountNum = 500L, status = "Order Cancelled") // Should be excluded from LTV
        val order4 = Order(id = "o4", orderAmountNum = 2000L, status = "RTO") // Should be excluded from LTV

        // Setup mock response for orders flow
        coEvery { repository.getOrdersForCustomer(phone) } returns flowOf(listOf(order1, order2, order3, order4))

        // Act
        viewModel.fetchCustomerData(phone)
        testDispatcher.scheduler.advanceUntilIdle() // Wait for coroutines to execute

        // Assert: 360-degree Validation Checks

        // 1. Total Orders exposed to UI should still show 4 (history is history)
        assertEquals("Total orders size must be 4", 4, viewModel.orders.value.size)

        // 2. LTV should only be 1000 + 1500 = 2500 (Excluding 500 Cancelled and 2000 RTO)
        assertEquals("LTV must exclude Cancelled and RTO orders", 2500L, viewModel.lifetimeValue.value)

        // 3. Active context lead should default to the parent (lead_1 with totalOrdersCount = 1)
        assertEquals("Context lead must default to parent lead", "lead_1", viewModel.activeLeadContext.value?.id)
        
        // 4. Fetched leads size should contain both duplicate entries
        assertEquals("Leads list must contain both duplicates", 2, viewModel.leads.value.size)
    }

    @Test
    fun `fetchCustomerData with zero orders results in zero LTV`() = runTest {
        val phone = "9876543210"
        val lead = Lead(id = "lead_fresh", phone = phone, name = "Fresh Lead")
        
        coEvery { repository.getCustomerLeads(phone) } returns listOf(lead)
        coEvery { repository.getOrdersForCustomer(phone) } returns flowOf(emptyList())
        coEvery { repository.getCustomerInteractions(any()) } returns emptyList()

        viewModel.fetchCustomerData(phone)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("LTV must be 0 for no orders", 0L, viewModel.lifetimeValue.value)
        assertEquals("Orders list must be empty", 0, viewModel.orders.value.size)
    }
}
