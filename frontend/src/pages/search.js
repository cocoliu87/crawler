import Head from "next/head";
import { Fragment, useEffect } from "react";
import { Layout as DashboardLayout } from "src/layouts/dashboard/layout";
import {
  Box,
  Button,
  Container,
  Stack,
  Typography,
  Card,
  InputAdornment,
  OutlinedInput,
  SvgIcon,
} from "@mui/material";
import MagnifyingGlassIcon from "@heroicons/react/24/solid/MagnifyingGlassIcon";
import Item from "../components/Item";
import { useState } from "react";

const mockData = [
  {
    id: "1",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "2",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "3",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "4",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "5",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "6",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "7",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "8",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "9",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "10",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "11",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "12",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "13",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "14",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "15",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "16",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "17",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "18",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "19",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "20",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "21",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
  {
    id: "22",
    url: "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards",
    text: "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.",
  },
];

const ur = "https://www.espn.com/nba/player/_/id/4594268/anthony-edwards";
const text =
  "Edwards sustained a right hip contusion Tuesday against Oklahoma City and will likely be unavailable for a second consecutive matchup Saturday. Assuming he's sidelined, Nickeil Alexander-Walker, Shake Milton and Troy Brown are candidates to see increased run again.";

const Page = () => {
  const [searchString, setSearchString] = useState("");
  const [all, setAll] = useState([]);
  const [currentList, setCurrentList] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemPerPage, setItemPerPage] = useState(5);
  const [isLoading, setIsLoading] = useState(false);
  const [count, setCount] = useState(0);

  useEffect(() => {
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const searchInputChangeHandler = (event) => {
    const input = event.target.value;
    setSearchString(input);
  };

  const handleScroll = () => {
    const { scrollTop, clientHeight, scrollHeight } = document.documentElement;
    console.log("scrollTop + clientHeight ", scrollTop + clientHeight);
    console.log("scrollHeight ", scrollHeight);
    if (scrollTop + clientHeight >= scrollHeight - 20) {
      setCurrentPage((prev) => prev + 1);
    }
  };

  useEffect(() => {
    fetch();
  }, [currentPage]);

  const search = () => {
    setAll((prev) => []);
    setCurrentList((prev) => []);

    const tmpCurrentPage = 1;
    setCurrentPage(1);
    // replace with the real search API
    // fetch the whole data
    const result = mockData;
    const list = result.slice((tmpCurrentPage - 1) * itemPerPage, tmpCurrentPage * itemPerPage);
    setAll(result);
    setCurrentList(list);
  };

  const fetch = () => {
    const list = all.slice((currentPage - 1) * itemPerPage, currentPage * itemPerPage);
    setCurrentList((prev) => [...prev, ...list]);
  };
  return (
    <Fragment>
      <Head>
        <title>Search | Devias Kit</title>
      </Head>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          py: 8,
        }}
      >
        <Container maxWidth="xl">
          <Stack spacing={3}>
            <Stack direction="row" justifyContent="space-between" spacing={4}>
              <Stack spacing={1}>
                <Typography variant="h4">Search</Typography>
              </Stack>
            </Stack>
            <Card sx={{ p: 2 }}>
              <OutlinedInput
                defaultValue=""
                fullWidth
                placeholder="Type to search information from crawler"
                onChange={searchInputChangeHandler}
                startAdornment={
                  <InputAdornment position="start">
                    <SvgIcon color="action" fontSize="small">
                      <MagnifyingGlassIcon />
                    </SvgIcon>
                  </InputAdornment>
                }
                sx={{ maxWidth: 500 }}
              />
              <Button variant="contained" style={{ margin: "10px" }} onClick={search}>
                Search
              </Button>
            </Card>
          </Stack>
        </Container>

        <Container maxWidth="xl">
          {currentList.map((item) => (
            <Item key={item.id} id={item.id} url={item.url} text={item.text} />
          ))}
        </Container>
      </Box>
    </Fragment>
  );
};

Page.getLayout = (page) => <DashboardLayout>{page}</DashboardLayout>;

export default Page;
