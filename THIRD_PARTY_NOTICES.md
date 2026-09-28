# Third-party notices

WheelLab itself is proprietary (see [LICENSE](LICENSE)). It uses the open-source libraries below, each under
its own license. A distributed build that contains them must keep these notices.

## Server (Java)

Included in `wheellab-server.jar`:

| Library | Version | License |
|---|---|---|
| [Gson](https://github.com/google/gson) (`com.google.code.gson:gson`) | 2.10.1 | Apache License 2.0 |

Used only by the tests (not included):

| Library | Version | License |
|---|---|---|
| [JUnit 5](https://junit.org/) (`junit-jupiter`) | 5.10.2 | Eclipse Public License 2.0 |

## Web app

Included in the built app (`web/dist`):

| Library | License |
|---|---|
| [React](https://react.dev/) (`react`, `react-dom`, and its dependency `scheduler`) | MIT |

Used only for development and building (not included): [Vite](https://vite.dev/),
`@vitejs/plugin-react`, [Oxlint](https://oxc.rs/) -- all MIT.

## Tools

The scripts in `tools/` use only the Python standard library.

## License texts

- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- MIT License: https://opensource.org/license/mit
- Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/

Exact versions are in `server/pom.xml` and `web/package-lock.json`.
