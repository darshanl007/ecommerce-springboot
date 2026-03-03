package com.dars.ecommerce.service.implementation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;

import com.dars.ecommerce.dto.Customer;
import com.dars.ecommerce.dto.CustomerOrder;
import com.dars.ecommerce.dto.Item;
import com.dars.ecommerce.dto.Product;
import com.dars.ecommerce.helper.AES;
import com.dars.ecommerce.helper.MyEmailSender;
import com.dars.ecommerce.repository.CustomerOrderRepository;
import com.dars.ecommerce.repository.CustomerRepository;
import com.dars.ecommerce.repository.ItemRepository;
import com.dars.ecommerce.repository.ProductRepository;
import com.dars.ecommerce.repository.SellerRepository;
import com.dars.ecommerce.service.CustomerService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    @Autowired
    CustomerOrderRepository orderRepository;

    @Autowired
    MyEmailSender emailSender;

    @Value("${razorpay.key}")
    private String key;

    @Value("${razorpay.secret}")
    private String secret;

    @Autowired
    ItemRepository itemRepository;

    

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    SellerRepository sellerRepository;

    @Autowired
    ProductRepository productRepository;

    @Override
    public String loadRegister(ModelMap map,Customer customer) {
        map.put("customer", customer);
        return "customer-register.html";
    }

    @Override
    public String loadRegister(@Valid Customer customer, BindingResult result, HttpSession session) {
        if (!customer.getPassword().equals(customer.getConfirmpassword())) {
            result.rejectValue("confirmpassword", "error.confirmpassword", "* Password Missmatch");
        }
        if (customerRepository.existsByEmail(customer.getEmail())
                || sellerRepository.existsByEmail(customer.getEmail())) {
            result.rejectValue("email", "error.email", "* Email should be Unique");
        }
        if (customerRepository.existsByMobile(customer.getMobile())
                || sellerRepository.existsByMobile(customer.getMobile())) {
            result.rejectValue("mobile", "error.mobile", "* Mobile Number should be Unique");
        }

        if (result.hasErrors()) {
            return "customer-register.html";
        } else {
            int otp = new Random().nextInt(100000, 1000000);
            customer.setOtp(otp);
            customer.setPassword(AES.encrypt(customer.getPassword(), "123"));
            customerRepository.save(customer);
            emailSender.sendOtp(customer);

            session.setAttribute("success", "Otp Sent Success");
            session.setAttribute("id", customer.getId());
            return "redirect:/customer/otp";
        }
    }

    @Override
    public String submitOtp(int id, int otp, HttpSession session) {
        Customer customer = customerRepository.findById(id).orElseThrow();
        if (customer.getOtp() == otp) {
            customer.setVerified(true);
            customerRepository.save(customer);
            session.setAttribute("success", "Account Created Success");
            return "redirect:/";
        } else {
            session.setAttribute("failure", "Invalid OTP");
            session.setAttribute("id", customer.getId());
            return "redirect:/customer/otp";
        }
    }

    @Override
    public String viewProducts(HttpSession session, ModelMap map) {
        if (session.getAttribute("customer") != null) {
            List<Product> products = productRepository.findByApprovedTrue();
            if (products.isEmpty()) {
                session.setAttribute("failure", "No Products Found");
                return "redirect:/customer/home";
            } else {
                Customer customer = (Customer) session.getAttribute("customer");
                map.put("items", customer.getCart().getItems());
                map.put("products", products);
                return "customer-products.html";
            }
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String loadHome(HttpSession session) {
        if (session.getAttribute("customer") != null) {
            return "customer-home.html";
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String addToCart(HttpSession session, int id) {
        if (session.getAttribute("customer") != null) {
            Product product = productRepository.findById(id).orElseThrow();
            if (product.getStock() < 1) {
                session.setAttribute("failure", "Out of Stock");
                return "redirect:/customer/products";
            } else {
                product.setStock(product.getStock() - 1);
                productRepository.save(product);

                Customer customer = (Customer) session.getAttribute("customer");

                List<Item> items = customer.getCart().getItems();
                if (items.isEmpty()) {
                    Item item = new Item();
                    item.setCategory(product.getCategory());
                    item.setName(product.getName());
                    item.setDescription(product.getDescription());
                    item.setImageLink(product.getImageLink());
                    item.setPrice(product.getPrice());
                    item.setQuantity(1);
                    items.add(item);

                    customer.getCart().setItems(items);

                    session.setAttribute("success", "Added to Cart Success");
                } else {
                    boolean flag = true;

                    for (Item item : items) {
                        if (item.getName().equalsIgnoreCase(product.getName())) {
                            item.setPrice(item.getPrice() + product.getPrice());
                            item.setQuantity(item.getQuantity() + 1);
                            flag = false;
                        }
                    }

                    if (flag) {
                        Item item = new Item();
                        item.setCategory(product.getCategory());
                        item.setName(product.getName());
                        item.setDescription(product.getDescription());
                        item.setImageLink(product.getImageLink());
                        item.setPrice(product.getPrice());
                        item.setQuantity(1);
                        items.add(item);

                    }
                    customer.getCart().setItems(items);

                    session.setAttribute("success", "Added to Cart Success");
                }

                customer.getCart()
                        .setPrice(customer.getCart().getItems().stream().mapToDouble(x -> x.getPrice()).sum());
                customerRepository.save(customer);

                session.setAttribute("customer", customerRepository.findById(customer.getId()).orElseThrow());
                return "redirect:/customer/products";
            }
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String resendOtp(int id, HttpSession session) {
        Customer customer = customerRepository.findById(id).orElseThrow();
        int otp = new Random().nextInt(100000, 1000000);
        customer.setOtp(otp);
        customerRepository.save(customer);
        emailSender.sendOtp(customer);

        session.setAttribute("success", "Otp Resent Success");
        session.setAttribute("id", customer.getId());
        return "redirect:/customer/otp";
    }

    @Override
    public String removeFromCart(HttpSession session, int id) {
        if (session.getAttribute("customer") == null) {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }

        try {
            Product product = productRepository.findById(id).orElseThrow();
            Customer sessionCustomer = (Customer) session.getAttribute("customer");
            Customer customer = customerRepository.findById(sessionCustomer.getId()).orElseThrow();
            List<Item> items = customer.getCart().getItems();
            // Defensive cleanup: remove stale cart references to deleted Item rows.
            items.removeIf(x -> x.getId() != 0 && !itemRepository.existsById(x.getId()));
            if (items.isEmpty()) {
                session.setAttribute("failure", "No Item in Cart");
            } else {
                Item item2 = null;
                for (Item item : items) {
                    if (item.getName().equals(product.getName())) {
                        item2 = item;
                        break;
                    }
                }
                if (item2 == null) {
                    session.setAttribute("failure", "No Item in Cart");
                } else {
                    product.setStock(product.getStock() + 1);
                    productRepository.save(product);
                    session.setAttribute("success", "Item Removed Success");
                    if (item2.getQuantity() > 1) {
                        item2.setQuantity(item2.getQuantity() - 1);
                        item2.setPrice(item2.getPrice() - product.getPrice());
                        itemRepository.save(item2);
                    } else {
                        customer.getCart().getItems().remove(item2);
                        customerRepository.save(customer);
                        if (itemRepository.existsById(item2.getId())) {
                            itemRepository.deleteById(item2.getId());
                        }
                    }
                }
            }
            customer.getCart().setPrice(customer.getCart().getItems().stream().mapToDouble(x -> x.getPrice()).sum());
            customerRepository.save(customer);
            session.setAttribute("customer", customerRepository.findById(customer.getId()).orElseThrow());
            return "redirect:/customer/products";
        } catch (Exception e) {
            session.setAttribute("failure", "Cart out of sync, refreshed cart");
            Customer sessionCustomer = (Customer) session.getAttribute("customer");
            session.setAttribute("customer", customerRepository.findById(sessionCustomer.getId()).orElseThrow());
            return "redirect:/customer/cart";
        }
    }

    @Override
    public String viewCart(HttpSession session, ModelMap map) {
        if (session.getAttribute("customer") != null) {
            Customer customer = (Customer) session.getAttribute("customer");
            if (customer.getCart().getItems().isEmpty()) {
                session.setAttribute("failure", "No Item in Cart");
                return "redirect:/customer/home";
            } else {
                map.put("cart", customer.getCart());
                return "customer-cart.html";
            }
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String addToCartItem(HttpSession session, int id) {
        if (session.getAttribute("customer") != null) {
            Item item = itemRepository.findById(id).orElseThrow();
            Product product = productRepository.findByName(item.getName()).get(0);
            return addToCart(session, product.getId());

        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String removeFromCartItem(HttpSession session, int id) {
        if (session.getAttribute("customer") != null) {
            Item item = itemRepository.findById(id).orElseThrow();
            Product product = productRepository.findByName(item.getName()).get(0);
            return removeFromCart(session, product.getId());
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String checkout(HttpSession session, ModelMap map) {
        if (session.getAttribute("customer") != null) {
            Customer customer = (Customer) session.getAttribute("customer");
            if (customer.getCart().getItems().isEmpty()) {
                session.setAttribute("failure", "No Item in Cart");
                return "redirect:/customer/home";
            } else {

                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", (int)(customer.getCart().getPrice() * 100));
                orderRequest.put("currency", "INR");

                com.razorpay.Order razorpayOrder;
                try {
                    RazorpayClient client = new RazorpayClient(key, secret);
                    razorpayOrder = client.orders.create(orderRequest);
                    if (razorpayOrder == null) {
                        log.error("Razorpay returned null order");
                        session.setAttribute("failure", "Unable to create payment order");
                        return "redirect:/customer/home";
                    }
                } catch (RazorpayException ex) {
                    log.error("Razorpay error creating order", ex);
                    session.setAttribute("failure", "Unable to create payment order");
                    return "redirect:/customer/home";
                } catch (Exception ex) {
                    log.error("Unexpected error creating Razorpay order", ex);
                    session.setAttribute("failure", "Payment gateway error");
                    return "redirect:/customer/home";
                }

                map.put("key", key);
                map.put("totalAmount", customer.getCart().getPrice());
                map.put("customer", customer);
                map.put("orderId", razorpayOrder.get("id"));
                map.put("cart", customer.getCart());

                CustomerOrder order1 = new CustomerOrder();
                List<Item> newItems = new ArrayList<>();
                for (Item item : customer.getCart().getItems()) {
                    Item newItem = new Item();
                    newItem.setName(item.getName());
                    newItem.setPrice(item.getPrice());
                    newItem.setQuantity(item.getQuantity());
                    newItem.setCategory(item.getCategory());
                    newItem.setDescription(item.getDescription());
                    newItem.setImageLink(item.getImageLink());
                    newItems.add(newItem);
                }

                order1.setItems(newItems);
                order1.setTotalAmount(customer.getCart().getPrice());
                order1.setOrderId(razorpayOrder.get("id"));
                order1.setCustomer(customer);

                orderRepository.save(order1);

                map.put("id", order1.getId());
                map.put("customer", customer);

                session.setAttribute("customer", customerRepository.findById(customer.getId()).orElseThrow());
                return "booking-confirmation-page.html";
            }
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String confirmOrder(HttpSession session, int id, String razorpay_payment_id) {
        if (session.getAttribute("customer") != null) {
            Customer customer = (Customer) session.getAttribute("customer");

            List<Integer> itemIds = customer.getCart().getItems().stream().mapToInt(x -> x.getId()).boxed()
                    .collect(Collectors.toList());
            customer.getCart().getItems().clear();
            customerRepository.save(customer);
            itemRepository.deleteAllById(itemIds);

            CustomerOrder customerOrder = orderRepository.findById(id).orElseThrow();
            customerOrder.setOrderDateTime(LocalDateTime.now());
            customerOrder.setPaymentId(razorpay_payment_id);
            orderRepository.save(customerOrder);
            session.setAttribute("success", "Order Placed Successfully");
            return "redirect:/customer/home";
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

    @Override
    public String viewOrders(HttpSession session, ModelMap map) {
        if (session.getAttribute("customer") != null) {
            Customer customer = (Customer) session.getAttribute("customer");
            List<CustomerOrder> orders = orderRepository.findByCustomerAndPaymentIdIsNotNull(customer);
            if (orders.isEmpty()) {
                session.setAttribute("failure", "No Orders Found");
                return "redirect:/customer/home";
            } else {
                map.put("orders", orders);
                return "customer-order-history.html";
            }
        } else {
            session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
        }
    }

}
