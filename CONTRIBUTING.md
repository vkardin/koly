# Contributing to Koly Ye

Thanks for helping! Bug reports, wrong schedules, new sources and pull requests are all welcome.

## Reporting problems

Open an [issue](https://github.com/vkardin/koly/issues/new/choose) and pick a template.
For a wrong schedule, include the region, the group, the date and a link to the official schedule.

## Data parsers (`data/`)

You need Python 3.12.

```sh
cd data
pip install -r requirements.txt
python -m pytest -q
python run.py --site ../site --only dtek-oem   # fetch one provider into ../site/v1
```

To add a region, follow [Adding a provider](data/SOURCES.md#adding-a-provider): save a real
response as a fixture, write a pure parsing function, raise `ProviderError` on anything
unexpected and add tests. Never publish empty or broken data over valid data.

## Windows widget (`desktop/`)

You need Node.js LTS and the stable Rust toolchain (`x86_64-pc-windows-msvc`) with the
Visual Studio C++ build tools.

```sh
cd desktop
npm install
npm test
npm run tauri dev
```

## Pull requests

- Branch from `main`: `feat/...`, `fix/...` or `chore/...`.
- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat(data): add Lviv provider`.
- Code, comments and commit messages are in English. Every UI string goes into all three
  languages: Українська, English, Русский.
- Do not use DTEK, YASNO or oblenergo logos, colours or icons.
- A new dependency needs a reason in the pull request description.
