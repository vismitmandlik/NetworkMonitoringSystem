# Network Monitoring System (NMS)

A robust and scalable **Network Monitoring System** designed to monitor network devices, analyze traffic, track performance metrics, and send alerts for issues or outages. This system provides a web interface for administrators to monitor the health of devices, obtain real-time performance data, and generate reports.

## Features

- **Real-Time Monitoring**: Track the health, bandwidth, and status of devices in your network.
- **Device Performance Metrics**: Monitor key metrics like CPU usage, memory, and bandwidth utilization.
- **Alerts and Notifications**: Receive notifications and alerts for network issues, device failures, or breaches of configured thresholds.
- **Scalability**: Built to scale with the size of your network, suitable for both small office setups and large enterprise networks.
- **RESxTful API**: Interact programmatically with the system to fetch data, configure devices, and manage alerts.
- **Historical Data**: View past performance data and logs to analyze trends and issues.

## Technologies Used

- **Backend**: Java
- **Build Tool**: Apache Maven
- **Security**: SSL/TLS 

## Installation

### Prerequisites

Ensure the following software is installed before proceeding:

- **Java Development Kit (JDK)**: Version 8 or higher.
- **Apache Maven**: For building the project.
- **Database**: MongoDB
- **Other Tools**: [List any other tools or libraries used]

### Step 1: Clone the Repository

Start by cloning the NMS repository to your local machine:

```bash
git clone https://github.com/vismitmandlik/NetworkMonitoringSystem.git
cd NetworkMonitoringSystem
```

### Step 2: Configure the System

- **Environment Variables**: Rename the `.env` file to set up your environment variables. Ensure all necessary variables are correctly configured.
- **Database Configuration**: Update the database configuration in the `application.properties` or equivalent configuration file.
- **SSL/TLS Configuration**: Ensure that `keystore.jks` and `mycert.crt` are properly configured for secure communication.

### Step 3: Build the Project

Use Maven to build the project:

```bash
mvn clean install
```

### Step 4: Run the Application

After building, run the application using:

```bash
java -jar target/NetworkMonitoringSystem.jar
```

By default, the system will be available at:

- **Web Interface**: `http://localhost:8080`
- **API Endpoint**: `http://localhost:8080/api`

## Usage

Once the system is running, you can interact with the web interface or use the API.

### Web Interface

- **Dashboard**: View the overall status of the network and all monitored devices.
- **Device Monitoring**: Add devices for monitoring by IP address or hostname. Track their real-time performance metrics.
- **Alerts**: Configure thresholds for bandwidth, CPU usage, etc., and receive alerts when these thresholds are breached.

### API Endpoints

[Provide detailed information about the available API endpoints, request/response formats, and examples.]

## Configuration

The application allows the following configuration options:

- **Devices**: Configure the devices to monitor, including type, IP address, and other parameters.
- **Thresholds**: Set thresholds for alerts based on device metrics (CPU, bandwidth, etc.).
- **Notifications**: Configure notification mechanisms such as email, SMS, or webhook alerts.

Configuration can be done via the web interface or by modifying the appropriate configuration files.

## Running Tests

To run unit tests, use the following command:

```bash
mvn test
```

Tests are located in the `src/test` directory.

## Contributing

We welcome contributions! If you'd like to contribute to this project, please follow these steps:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/your-feature`).
3. Make your changes and commit them (`git commit -am 'Add new feature'`).
4. Push to the branch (`git push origin feature/your-feature`).
5. Create a pull request.

Please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for more details.

## Acknowledgements

- **Java**: For building the backend.
- **Apache Maven**: For project build and dependency management.
- **SSL/TLS**: For securing communications.

---

Feel free to contact the project maintainers for any inquiries.
